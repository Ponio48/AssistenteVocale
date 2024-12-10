interface WikipediaApiService {
    @GET("api.php")
    suspend fun search(
        @Query("action") action: String = "query",
        @Query("format") format: String = "json",
        @Query("prop") prop: String = "extracts",
        @Query("exintro") exintro: Boolean = true,
        @Query("explaintext") explaintext: Boolean = true,
        @Query("titles") titles: String,
        @Query("language") language: String
    ): WikiResponse
}

data class WikiResponse(
    val query: QueryResult?
)

data class QueryResult(
    val pages: Map<String, WikiPage>
)

data class WikiPage(
    val extract: String
) 