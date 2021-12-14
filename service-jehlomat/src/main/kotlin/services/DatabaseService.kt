package services

import api.*
import model.*
import model.pagination.OrderByDefinition
import model.pagination.PageInfo
import model.pagination.toDsl
import model.syringe.SyringeFilter
import model.syringe.OrderBySyringeColumn
import org.ktorm.database.Database
import org.ktorm.database.asIterable
import org.ktorm.dsl.*
import org.ktorm.expression.SqlFormatter
import org.ktorm.schema.ColumnDeclaring
import org.ktorm.support.postgresql.PostgreSqlFormatter
import org.ktorm.support.postgresql.insertOrUpdateReturning
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import utils.hashPassword
import kotlin.streams.toList


private const val NUMBER_OF_INSERT_SYRINGE_TRIES = 100

class DatabaseService(
    host: String = System.getenv("DATABASE_HOST"),
    port: String = System.getenv("DATABASE_PORT"),
    database: String = System.getenv("DATABASE_NAME"),
    user: String = System.getenv("DATABASE_USERNAME"),
    password: String = System.getenv("PGPASSWORD") ?: ""
) {
    private val databaseInstance = Database.connect(
        "jdbc:postgresql://$host:$port/$database", user = user, password = password
    )
    private val syringeIdGenerator = SyringeIdGenerator()

    private val syringeCreatedByAlias = UserTable.aliased("uCreatedBy")
    private val syringeReservedByAlias = UserTable.aliased("uReservedBy")
    private val syringeDemolishedByAlias = UserTable.aliased("uDemolishedBy")

    private val syringeSelectColumns: MutableList<ColumnDeclaring<*>> = mutableListOf()

    init {
        syringeSelectColumns.addAll(SyringeTable.columns)
        syringeSelectColumns.addAll(LocationTable.columns)
        syringeSelectColumns.addAll(syringeCreatedByAlias.columns)
        syringeSelectColumns.addAll(syringeReservedByAlias.columns)
        syringeSelectColumns.addAll(syringeDemolishedByAlias.columns)
    }

    private val mapSyringeRow: (row: QueryRowSet) -> Syringe = { row ->
        Syringe(
            id = row[SyringeTable.id]!!,
            createdAt = row[SyringeTable.createdAt]!!,
            createdBy = mapUserInfoRow(row, syringeCreatedByAlias),
            reservedTill = row[SyringeTable.reservedTill],
            reservedBy = mapUserInfoRow(row, syringeReservedByAlias),
            demolishedAt = row[SyringeTable.demolishedAt],
            demolishedBy = mapUserInfoRow(row, syringeDemolishedByAlias),
            photo = row[SyringeTable.photo]!!,
            count = row[SyringeTable.count]!!,
            note = row[SyringeTable.note] ?: "",
            demolisherType = Demolisher.valueOf(row[SyringeTable.demolisherType]!!),
            gps_coordinates = row[SyringeTable.gpsCoordinates]!!,
            demolished = row[SyringeTable.demolished]!!,
            location = mapLocationRow(row),
        )
    }

    private val mapUserInfoRow: (row: QueryRowSet, table: UserTable) -> UserInfo? = { row, table ->
        if (row[table.userId] != null) {
            UserInfo(
                id = row[table.userId]!!,
                email = row[table.email]!!,
                username = row[table.username]!!,
                verified = row[table.verified]!!,
                organizationId = row[table.organizationId]!!,
                teamId = row[table.teamId],
                isAdmin = row[table.isAdmin]!!
            )
        } else {
            null
        }
    }

    private val mapUserRow: (row: QueryRowSet) -> User = { row ->
       User(
            id = row[UserTable.userId]!!,
            email = row[UserTable.email]!!,
            username = row[UserTable.username]!!,
            password = row[UserTable.password]!!,
            verified = row[UserTable.verified]!!,
            organizationId = row[UserTable.organizationId]!!,
            teamId = row[UserTable.teamId],
            isAdmin = row[UserTable.isAdmin]!!
        )
    }

    private val syringeSelectCommonQuery = databaseInstance
        .from(SyringeTable)
        .innerJoin(LocationTable, LocationTable.id eq SyringeTable.locationId)
        .leftJoin(syringeCreatedByAlias, syringeCreatedByAlias.userId eq SyringeTable.createdBy)
        .leftJoin(syringeReservedByAlias, syringeReservedByAlias.userId eq SyringeTable.reservedBy)
        .leftJoin(syringeDemolishedByAlias, syringeDemolishedByAlias.userId eq SyringeTable.demolishedBy)
        .select(syringeSelectColumns)

    fun selectSyringeById(id: String): Syringe? {
        return syringeSelectCommonQuery
            .where { SyringeTable.id eq id }
            .map(mapSyringeRow)
            .firstOrNull()
    }

    fun selectSyringes(): List<Syringe> {
        return syringeSelectCommonQuery
            .orderBy(SyringeTable.id.asc())
            .map(mapSyringeRow)
    }

    fun selectSyringes(filter: SyringeFilter, pageInfo: PageInfo, orderByList: List<OrderByDefinition<OrderBySyringeColumn>>): List<Syringe> {
        val filterDsl = SyringeFilterTransformer.filterToDsl(filter, syringeCreatedByAlias)

        return syringeSelectCommonQuery
            .where { filterDsl }
            .limit(pageInfo.index, pageInfo.size + 1)
            .orderBy(orderByList.stream().map{
                it.toDsl(mapOf(Pair(OrderBySyringeColumn.CREATED_BY, syringeCreatedByAlias)))
            }.toList())
            .map(mapSyringeRow)
    }

    fun selectUserById(id: Int): User? {
        return databaseInstance
            .from(UserTable)
            .select()
            .where { UserTable.userId eq id }
            .map(mapUserRow)
            .firstOrNull()
    }

    fun selectUserByEmail(email: String): User? {
        return databaseInstance
            .from(UserTable)
            .select()
            .where { UserTable.email eq email }
            .map(mapUserRow)
            .firstOrNull()
    }

    fun findAdmin(organization: Organization): User {
        return databaseInstance
            .from(UserTable)
            .select()
            .where { (UserTable.organizationId eq organization.id) and (UserTable.isAdmin eq true) }
            .map(mapUserRow)
            .first()
    }

    private val mapLocationRow: (row: QueryRowSet) -> Location = { row ->
        Location(
            id = row[LocationTable.id]!!,
            obec = row[LocationTable.obec]!!,
            okres = row[LocationTable.okres]!!,
            mestkaCast = row[LocationTable.mestka_cast]!!,
        )
    }

    private val mapTeamRow: (row: QueryRowSet) -> Team = { row ->
        Team(
            id = row[TeamTable.teamId]!!,
            name = row[TeamTable.name]!!,
            location = mapLocationRow(row),
            organizationId = row[TeamTable.organization_id]!!
        )
    }

    fun selectTeams(): List<Team> {
        return databaseInstance
            .from(TeamTable)
            .innerJoin(LocationTable, LocationTable.id eq TeamTable.location_id)
            .select()
            .orderBy(TeamTable.name.asc())
            .map(mapTeamRow)
    }

    fun selectTeamById(id: Int): Team? {
        return databaseInstance
            .from(TeamTable)
            .innerJoin(LocationTable, LocationTable.id eq TeamTable.location_id)
            .select()
            .where { TeamTable.teamId eq id }
            .map(mapTeamRow)
            .firstOrNull()
    }

    fun selectTeamByName(name: String): Team? {
        return databaseInstance
            .from(TeamTable)
            .innerJoin(LocationTable, LocationTable.id eq TeamTable.location_id)
            .select()
            .where { TeamTable.name eq name }
            .map(mapTeamRow)
            .firstOrNull()
    }

    fun selectOrganizations(): List<Organization> {
        return databaseInstance
            .from(OrganizationTable)
            .select()
            .orderBy(OrganizationTable.name.asc())
            .map { row -> OrganizationTable.createEntity(row) }
    }

    fun updateUser(user: User) {
        databaseInstance.update(UserTable) {
            set(it.email, user.email)
            set(it.password, user.password.hashPassword())
            set(it.username, user.username)
            set(it.verified, user.verified)
            set(it.organizationId, user.organizationId)
            set(it.teamId, user.teamId)
            set(it.isAdmin, user.isAdmin)
        }
    }

    fun selectOrganizationById(id: Int): Organization? {
        return databaseInstance
            .from(OrganizationTable)
            .select()
            .where { OrganizationTable.organizationId eq id }
            .map { row -> OrganizationTable.createEntity(row) }
            .firstOrNull()
    }

    fun selectOrganizationByName(name: String): Organization? {
        return databaseInstance
            .from(OrganizationTable)
            .select()
            .where { OrganizationTable.name eq name }
            .map { row -> OrganizationTable.createEntity(row) }
            .firstOrNull()
    }

    fun updateOrganization(organization: Organization) {
        databaseInstance.update(OrganizationTable) {
            set(it.name, organization.name)
            set(it.verified, organization.verified)
        }
    }

    fun updateTeam(team: Team) {
        databaseInstance.update(TeamTable) {
            set(it.name, team.name)
            set(it.location_id, getLocationId(team))
            set(it.organization_id, team.organizationId)
        }
    }

    fun insertSyringe(syringe: Syringe): String? {
        for (i in 1 .. NUMBER_OF_INSERT_SYRINGE_TRIES) {
            val id = syringeIdGenerator.generateId()
            try {
                databaseInstance.insert(SyringeTable) {
                    set(it.id, id)
                    updateSyringeRecord(this, it, syringe)
                }
                return id
            } catch (e: PSQLException) {
                if (e.sqlState != PSQLState.UNIQUE_VIOLATION.state) {
                    throw e
                }
            }
        }

        return null
    }

    fun updateSyringe(syringe: Syringe) {
        databaseInstance.update(SyringeTable) {
            updateSyringeRecord(this, it, syringe)
        }
    }

    private fun updateSyringeRecord(builder: AssignmentsBuilder, it: SyringeTable, syringe: Syringe) {
        builder.set(it.createdAt, syringe.createdAt)
        builder.set(it.createdBy, syringe.createdBy?.id)
        builder.set(it.reservedTill, syringe.reservedTill)
        builder.set(it.reservedBy, syringe.reservedBy?.id)
        builder.set(it.demolishedAt, syringe.demolishedAt)
        builder.set(it.demolishedBy, syringe.demolishedBy?.id)
        builder.set(it.demolisherType, syringe.demolisherType.name)
        builder.set(it.photo, syringe.photo)
        builder.set(it.count, syringe.count)
        builder.set(it.note, syringe.note)
        builder.set(it.gpsCoordinates, syringe.gps_coordinates)
        builder.set(it.demolished, syringe.demolished)
        builder.set(it.locationId, syringe.location.id)
    }


    fun insertUser(user: User): Int {
        return databaseInstance.insertAndGenerateKey(UserTable) {
            set(it.email, user.email)
            set(it.password, user.password.hashPassword())
            set(it.username, user.username)
            set(it.verified, user.verified)
            set(it.organizationId, user.organizationId)
            set(it.teamId, user.teamId)
            set(it.isAdmin, user.isAdmin)
        } as Int
    }

    fun insertOrganization(organization: Organization): Int {
        return databaseInstance.insertAndGenerateKey(OrganizationTable) {
            set(it.name, organization.name)
            set(it.verified, organization.verified)
        } as Int
    }

    fun deleteOrganization(organization: Organization) {
        databaseInstance.delete(OrganizationTable) { it.name eq organization.name }
    }

    fun insertTeam(team: Team): Int {
        return databaseInstance.insertAndGenerateKey(TeamTable) {
            set(it.organization_id, team.organizationId)
            set(it.location_id, getLocationId(team))
            set(it.name, team.name)
        } as Int
    }

    private fun getLocationId(team: Team): Int {
        insertLocation(team.location.okres, team.location.mestkaCast, team.location.obec)

        return databaseInstance
            .from(LocationTable)
            .select()
            .where(
                (LocationTable.mestka_cast eq team.location.mestkaCast)
                        and (LocationTable.obec eq team.location.obec)
                        and (LocationTable.okres eq team.location.okres)
            )
            .map { it.getInt("location_id") }.first()
    }

    fun deleteSyringe(id: String) {
        databaseInstance.delete(SyringeTable) { it.id eq id }
    }

    fun deleteTeam(name: String) {
        databaseInstance.delete(TeamTable) { it.name eq name }
    }

    fun postgisLocation(table: String, gpsCoordinates: String, column: String): String {
        val names = databaseInstance.useConnection { conn ->
            val sql =
                "SELECT $column FROM $table WHERE ST_Within('POINT( $gpsCoordinates )'::geometry, $table.wkb_geometry)"

            conn.prepareStatement(sql).use { statement ->
                statement.executeQuery().asIterable().map { it.getString(1) }
            }
        }

        return names.firstOrNull() ?: ""
    }

    fun getObec(gpsCoordinates: String): String {
        return postgisLocation("sph_obec", gpsCoordinates, "kod_lau2")
    }

    fun getMC(gpsCoordinates: String): String {
        return postgisLocation("sph_mc", gpsCoordinates, "kod_mc")
    }

    fun getOkres(gpsCoordinates: String): String {
        return postgisLocation("sph_okres", gpsCoordinates, "kod_lau1")
    }

    fun getLocations(): List<Map<String, String>> {
        val okres = getLocations("kod_lau1", "nazev_lau1", "sph_okres", "okres")
        val obce = getLocations("kod_lau2", "nazev_lau2", "sph_obec", "obec")
        val mc = getLocations("kod_mc", "nazev_mc", "sph_mc", "mc")
        return okres + obce + mc
    }

    private fun getLocations(idColumn: String, nameColumn: String, table: String, type: String): List<Map<String, String>> {
        return databaseInstance.useConnection { conn ->
            val sql = "SELECT $idColumn, $nameColumn FROM $table"
            conn.prepareStatement(sql).use { statement ->
                statement.executeQuery().asIterable().map {
                    mapOf(
                        "id" to it.getString(1),
                        "name" to it.getString(2),
                        "type" to type
                    )
                }
            }
        }
    }

    fun getLocationCombinations(gpsCoordinates: String): List<Location> {
        val obec = getObec(gpsCoordinates)
        val mc = getMC(gpsCoordinates)
        val okres = getOkres(gpsCoordinates)

        return listOfNotNull(
            Location(0, okres, obec, mc),
            Location(0, okres, obec, ""),
            Location(0, okres, "", ""),
        )
    }

    fun insertLocation(district: String, locality: String, town:String): Location {
        val id = databaseInstance.insertOrUpdateReturning(LocationTable, LocationTable.id) {
            set(it.mestka_cast, locality)
            set(it.okres, district)
            set(it.obec, town)
            onConflict(it.mestka_cast, it.okres, it.obec) { doNothing() }
        }

        return if (id != null) {
            selectLocationInner(LocationTable.id eq id)!!
        } else {
            selectLocation(district, locality, town)!!
        }
    }

    private fun selectLocation(gpsCoordinates: String): Location? {
        val town = getObec(gpsCoordinates)
        val locality = getMC(gpsCoordinates)
        val district = getOkres(gpsCoordinates)

        return selectLocation(district, locality, town)
    }

    private fun selectLocation(district: String, locality: String, town: String): Location? {
        return (selectLocationInner((LocationTable.mestka_cast eq locality) and (LocationTable.obec eq town) and (LocationTable.okres eq district))
            ?: run { selectLocationInner((LocationTable.obec eq town) and (LocationTable.okres eq district)) }
            ?: run { selectLocationInner(LocationTable.okres eq district) })
    }

    private fun selectLocationInner(condition: ColumnDeclaring<Boolean>): Location? {
        return databaseInstance.from(LocationTable)
            .select()
            .where { condition }
            .map { row -> mapLocationRow(row) }
            .firstOrNull()
    }

    fun selectOrInsertLocation(gpsCoordinates: String): Location {
        val town = getObec(gpsCoordinates)
        val locality = getMC(gpsCoordinates)
        val district = getOkres(gpsCoordinates)

        return selectLocation(district, locality, town) ?: insertLocation(district, locality, town)
    }

    fun resolveNearestTeam(gpsCoordinates: String): Team? {
        return resolveTeamsInLocation(gpsCoordinates).firstOrNull()
    }

    fun resolveTeamsInLocation(gpsCoordinates: String): Set<Team> {
        val location = selectLocation(gpsCoordinates) ?: return setOf()

        return databaseInstance
            .from(TeamTable)
            .select()
            .where { TeamTable.location_id eq location.id }
            .map { row ->
                Team(
                    id=row.getInt("team_id"),
                    name=row.getString("name")!!,
                    location=location,
                    organizationId=row.getInt("organization_id"),
                )
            }
            .toHashSet()
    }

    fun cleanLocation(): Int {
        return databaseInstance.deleteAll(LocationTable)
    }

    fun cleanTeams(): Int {
        return databaseInstance.deleteAll(TeamTable)
    }

    fun cleanUsers(): Int {
        return databaseInstance.deleteAll(UserTable)
    }

    fun cleanOrganizations(): Int {
        return databaseInstance.deleteAll(OrganizationTable)
    }

    fun cleanSyringes(): Int {
        return databaseInstance.deleteAll(SyringeTable)
    }

    fun <T> useTransaction(func: () -> T): T {
        databaseInstance.useTransaction { return func.invoke() }
    }

    fun createSqlFormatter(): SqlFormatter {
        return  PostgreSqlFormatter(databaseInstance, false, 0)
    }
}
