interface DuckDuckGoApiService {
    @GET("/?format=json")
    suspend fun instantAnswer(
        @Query("q") query: String
    ): DuckDuckGoResponse
}

data class DuckDuckGoResponse(
    @SerializedName("AbstractText")
    val abstractText: String = "",
    @SerializedName("Abstract")
    val abstract: String = ""
) 