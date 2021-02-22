package services.places

import utils.Either

interface IPlaces {
    fun autocomplete(
        search: String,
        onComplete: (Either<List<AutocompleteResponse>>) -> Unit
    )

    fun geolocation(
        placeId: String,
        onComplete: (Either<String>) -> Unit
    )
}

data class AutocompleteResponse(
    val description: String,
    val placeId: String
)