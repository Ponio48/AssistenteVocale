class WebSearchManager {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.duckduckgo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val wikipediaRetrofit = Retrofit.Builder()
        .baseUrl("https://it.wikipedia.org/w/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val newsRetrofit = Retrofit.Builder()
        .baseUrl("https://newsapi.org/v2/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    suspend fun searchInfo(query: String): String {
        return try {
            when {
                isNewsQuery(query) -> searchNews(query)
                isWikiQuery(query) -> searchWikipedia(query)
                else -> generalSearch(query)
            }
        } catch (e: Exception) {
            "Mi dispiace, non sono riuscita a trovare informazioni su questo argomento."
        }
    }

    private fun isNewsQuery(query: String): Boolean {
        return query.contains(Regex("(notizie|news|attualità|ultime|recenti)"))
    }

    private fun isWikiQuery(query: String): Boolean {
        return query.contains(Regex("(chi è|cos'è|significa|definizione|storia di)"))
    }

    private suspend fun searchNews(query: String): String {
        // Implementazione della ricerca notizie usando NewsAPI
        val response = newsRetrofit.create(NewsApiService::class.java)
            .getNews(query, "it", BuildConfig.NEWS_API_KEY)
        
        return if (response.articles.isNotEmpty()) {
            val article = response.articles[0]
            "${article.title}. ${article.description}"
        } else {
            "Non ho trovato notizie recenti su questo argomento."
        }
    }

    private suspend fun searchWikipedia(query: String): String {
        // Implementazione della ricerca su Wikipedia
        val response = wikipediaRetrofit.create(WikipediaApiService::class.java)
            .search(query, "it")
        
        return response.query?.pages?.values?.firstOrNull()?.extract
            ?: "Non ho trovato informazioni su Wikipedia per questo argomento."
    }

    private suspend fun generalSearch(query: String): String {
        // Implementazione della ricerca generale usando DuckDuckGo
        val response = retrofit.create(DuckDuckGoApiService::class.java)
            .instantAnswer(query)
        
        return response.abstractText.takeIf { it.isNotEmpty() }
            ?: "Mi dispiace, non ho trovato una risposta precisa per questa domanda."
    }
} 