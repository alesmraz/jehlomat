# Run backend application locally

Used `jehlomat` as postgres database name and `jehlomat` as username to database. 
Please change it in example by your postgres configuration. 

1) Install postgres DB
2) Install postgis extensions (https://postgis.net/install/)
3) Add testing data

```shell
  psql -h localhost -p 5432 -U jehlomat -d jehlomat -f ./service-jehlomat/src/main/resources/postgis.sql
  psql -h localhost -p 5432 -U jehlomat -d jehlomat -f ./service-jehlomat/src/test/resources/obce.sql
  psql -h localhost -p 5432 -U jehlomat -d jehlomat -f ./service-jehlomat/src/test/resources/mc.sql
  psql -h localhost -p 5432 -U jehlomat -d jehlomat -f ./service-jehlomat/src/test/resources/okres.sql
  psql -h localhost -p 5432 -U jehlomat -d jehlomat -f ./service-jehlomat/src/main/resources/create_table.sql
```

4) Create the Magdalena organization and super admin with email `super@admin.cz` and password `SuperAdmin1`. Use this script only for testing purposes.
```shell
  psql -h localhost -p 5432 -U jehlomat -d jehlomat -f ./service-jehlomat/src/main/resources/insert_super_admin.sql
```
5) Update email api keys

```
mailjet {
    publicKey = ""
    privateKey = ""
}
```

in `service-jehlomat/src/main/resources/application.conf`

6) Set environment variables
```shell
export DATABASE_HOST=localhost
export DATABASE_PORT=5432
export DATABASE_USERNAME=jehlomat
export PGPASSWORD=<YOUR POSTGRES PASSWORD>
export DATABASE_NAME=jehlomat
export JWT_ISSUER=http://localhost:8082/
export JWT_AUDIENCE=http://localhost:8082/
export JWT_REALM=jehlomat_local_realm
export SUPER_ADMIN_EMAIL=super@admin.cz
```
7) Run application
```shell
./gradlew run --parallel
```

# Swagger UI

```
http://localhost:8082/swagger-ui/index.html?url=/static/swagger.yaml#/default
```

# DB extensions

### Postgis
https://postgis.net/install/

### RUIAN

https://geoportal.cuzk.cz/zakazky/SPH/SPH_SHP_WGS84.zip

```shell
unzip SPH_SHP_WGS84.zip
cd WGS84

ogr2ogr -f "PostgreSQL" PG:"host=localhost user=<USER> password=<PASSWORD> dbname=<DBNAME>" SPH_OBEC.shp
ogr2ogr -f "PostgreSQL" PG:"host=localhost user=<USER> password=<PASSWORD> dbname=<DBNAME>" SPH_MC.shp
ogr2ogr -f "PostgreSQL" PG:"host=localhost user=<USER> password=<PASSWORD> dbname=<DBNAME>" SPH_OKRES.shp
```

# Create test dataset

```shell
pg_dump -t 'sph_*' jehlomat > db.sql
```

Remove lines for sph_* tables to make small test sample

```shell
psql -h localhost -p 5432 -U jehlomat -d jehlomat < db.sql
```
