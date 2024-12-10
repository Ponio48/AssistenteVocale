interface NewsApiService {
    @GET("top-headlines")
    suspend fun getNews(
        @Query("q") query: String,
        @Query("language") language: String,
        @Query("apiKey") apiKey: String
    ): NewsResponse
}

data class NewsResponse(
    val articles: List<Article>
)

data class Article(
    val title: String,
    val description: String
) 