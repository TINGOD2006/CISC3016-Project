package com.webproject;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Web extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();
    private Random random = new Random();
    private static final int REQUEST_DELAY_MS = 1500;
    
    // Guardian API Key
    private static final String GUARDIAN_API_KEY = "5bc21cfa-ec62-47ba-a575-6124bb4b5a81";
    
    private String[] comments = {
        "🔥 即時爬取：今日科技新聞熱度持續上升！",
        "📈 最新即時資訊顯示市場反應正面！",
        "💡 從即時數據可以看出創新趨勢！",
        "🌍 全球科技圈最新動態即時更新！",
        "🎯 AI 領域今日有多項重要發布！"
    };
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String uri = request.getRequestURI();
        System.out.println("收到請求: " + uri);
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        if (uri.endsWith("/api/news")) {
            getLiveNews(response);
        } else if (uri.endsWith("/api/spellcheck")) {
            String word = request.getParameter("word");
            spellCheck(word, response);
        } else {
            request.getRequestDispatcher("/index.html").forward(request, response);
        }
    }
    
    private void getLiveNews(HttpServletResponse response) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> allNews = new ArrayList<>();
        
        System.out.println("開始即時爬取新聞...");
        long startTime = System.currentTimeMillis();
        
        // 來源1：The Guardian API
        try {
            List<Map<String, String>> guardianNews = crawlGuardianNews();
            allNews.addAll(guardianNews);
            System.out.println("The Guardian 爬取完成: " + guardianNews.size() + " 則");
            Thread.sleep(REQUEST_DELAY_MS);
        } catch (Exception e) {
            System.err.println("The Guardian 失敗: " + e.getMessage());
            allNews.add(createErrorNews("The Guardian"));
        }
        
        // 來源2：CNN 科技新聞
        try {
            List<Map<String, String>> cnnNews = crawlCNNNews();
            allNews.addAll(cnnNews);
            System.out.println("CNN 科技新聞爬取完成: " + cnnNews.size() + " 則");
            Thread.sleep(REQUEST_DELAY_MS);
        } catch (Exception e) {
            System.err.println("CNN 失敗: " + e.getMessage());
            allNews.add(createErrorNews("CNN Tech"));
        }
        
        // 來源3：BBC 新聞
        try {
            List<Map<String, String>> bbcNews = crawlBBCNews();
            allNews.addAll(bbcNews);
            System.out.println("BBC 新聞爬取完成: " + bbcNews.size() + " 則");
        } catch (Exception e) {
            System.err.println("BBC 失敗: " + e.getMessage());
            allNews.add(createErrorNews("BBC News"));
        }
        
        // 隨機打亂新聞順序
        Collections.shuffle(allNews);
        
        long endTime = System.currentTimeMillis();
        String randomComment = comments[random.nextInt(comments.length)];
        
        result.put("news", allNews);
        result.put("randomComment", randomComment);
        result.put("timestamp", new Date().toString());
        result.put("fetchTime", (endTime - startTime) + "ms");
        
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(result));
        out.flush();
        
        System.out.println("總共回傳: " + allNews.size() + " 則即時新聞（已隨機排序）");
    }
    
    // ==================== 1. The Guardian API 爬蟲 ====================
    
    private List<Map<String, String>> crawlGuardianNews() throws Exception {
        List<Map<String, String>> newsList = new ArrayList<>();
        
        // 爬取多個分類
        String[] sections = {"technology", "business", "world-news", "science"};
        
        for (String section : sections) {
            String apiUrl = "https://content.guardianapis.com/" + section + 
                            "?api-key=" + GUARDIAN_API_KEY +
                            "&show-fields=thumbnail,trailText,byline" +
                            "&page-size=3" +
                            "&order-by=newest";
            
            try {
                System.out.println("  呼叫 Guardian API: " + section);
                
                String jsonResponse = Jsoup.connect(apiUrl)
                        .userAgent("Mozilla/5.0")
                        .ignoreContentType(true)
                        .timeout(10000)
                        .execute()
                        .body();
                
                JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
                JsonObject responseObj = root.getAsJsonObject("response");
                JsonArray results = responseObj.getAsJsonArray("results");
                
                for (int i = 0; i < results.size() && newsList.size() < 12; i++) {
                    JsonObject article = results.get(i).getAsJsonObject();
                    JsonObject fields = article.has("fields") ? article.getAsJsonObject("fields") : new JsonObject();
                    
                    String title = article.get("webTitle").getAsString();
                    String link = article.get("webUrl").getAsString();
                    String sectionName = getGuardianSectionName(section);
                    
                    String imageUrl = fields.has("thumbnail") ? fields.get("thumbnail").getAsString() : getFallbackImage();
                    String summary = fields.has("trailText") ? fields.get("trailText").getAsString() : "";
                    String author = fields.has("byline") ? fields.get("byline").getAsString() : "The Guardian";
                    
                    Map<String, String> news = new HashMap<>();
                    news.put("title", title);
                    news.put("source", "The Guardian 📰 - " + sectionName);
                    news.put("link", link);
                    news.put("image", imageUrl);
                    news.put("score", "🔥 熱門");
                    news.put("summary", summary.length() > 120 ? summary.substring(0, 120) + "..." : summary);
                    news.put("author", author);
                    
                    newsList.add(news);
                    System.out.println("  Guardian: " + title.substring(0, Math.min(50, title.length())));
                }
                
                Thread.sleep(500);
                
            } catch (Exception e) {
                System.err.println("Guardian " + section + " 失敗: " + e.getMessage());
            }
        }
        
        return newsList;
    }
    
    private String getGuardianSectionName(String section) {
        Map<String, String> sectionMap = new HashMap<>();
        sectionMap.put("technology", "科技");
        sectionMap.put("business", "商業");
        sectionMap.put("world-news", "國際");
        sectionMap.put("culture", "文化");
        sectionMap.put("science", "科學");
        sectionMap.put("sport", "體育");
        sectionMap.put("politics", "政治");
        return sectionMap.getOrDefault(section, section);
    }
    
    // ==================== 2. CNN 科技新聞爬蟲 ====================
    
    private List<Map<String, String>> crawlCNNNews() throws Exception {
        List<Map<String, String>> newsList = new ArrayList<>();
        String url = "http://rss.cnn.com/rss/cnn_world.rss";
        
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get();
        
        Elements items = doc.select("item");
        
        for (int i = 0; i < items.size() && newsList.size() < 4; i++) {
            Element item = items.get(i);
            String title = item.select("title").text();
            String link = item.select("link").text();
            String description = item.select("description").text();
            
            title = title.replaceAll("<[^>]*>", "");
            description = description.replaceAll("<[^>]*>", "");
            
            String imageUrl = extractImageFromUrl(link);
            
            Map<String, String> news = new HashMap<>();
            news.put("title", title);
            news.put("source", "CNN 科技 🌐");
            news.put("link", link);
            news.put("image", imageUrl);
            news.put("score", "🔥 熱門");
            news.put("summary", description.length() > 120 ? description.substring(0, 120) + "..." : description);
            newsList.add(news);
            
            System.out.println("CNN: " + title.substring(0, Math.min(50, title.length())));
        }
        
        return newsList;
    }
    
    // ==================== 3. BBC 新聞爬蟲 ====================
    
    private List<Map<String, String>> crawlBBCNews() throws Exception {
        List<Map<String, String>> newsList = new ArrayList<>();
        String url = "https://feeds.bbci.co.uk/news/technology/rss.xml";
        
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();
        
        Elements items = doc.select("item");
        
        for (int i = 0; i < items.size() && newsList.size() < 4; i++) {
            Element item = items.get(i);
            String title = item.select("title").text();
            String link = item.select("link").text();
            String description = item.select("description").text();
            
            description = description.replaceAll("<[^>]*>", "");
            
            String imageUrl = extractImageFromUrl(link);
            
            Map<String, String> news = new HashMap<>();
            news.put("title", title);
            news.put("source", "BBC News 🌐");
            news.put("link", link);
            news.put("image", imageUrl);
            news.put("score", "🔥 熱門");
            news.put("summary", description.length() > 120 ? description.substring(0, 120) + "..." : description);
            newsList.add(news);
            
            System.out.println("BBC: " + title.substring(0, Math.min(50, title.length())));
        }
        
        return newsList;
    }
    
    // ==================== 從網址提取真實圖片 ====================
    
    private String extractImageFromUrl(String url) {
        if (url == null || url.isEmpty() || url.equals("#")) {
            return getFallbackImage();
        }
        
        try {
            System.out.println("  抓取圖片: " + url);
            
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(8000)
                    .ignoreHttpErrors(true)
                    .get();
            
            // 1. Open Graph 圖片
            Element ogImage = doc.select("meta[property='og:image']").first();
            if (ogImage != null) {
                String img = ogImage.attr("content");
                if (isValidImage(img)) {
                    System.out.println("    ✓ OG圖片");
                    return img;
                }
            }
            
            // 2. Twitter Card 圖片
            Element twitterImage = doc.select("meta[name='twitter:image']").first();
            if (twitterImage != null) {
                String img = twitterImage.attr("content");
                if (isValidImage(img)) {
                    System.out.println("    ✓ Twitter圖片");
                    return img;
                }
            }
            
            // 3. 文章中的第一張大圖
            String[] selectors = {
                "article img", ".article img", ".post-content img", 
                ".entry-content img", "main img", ".content img",
                "div[class*='image'] img", "figure img"
            };
            for (String selector : selectors) {
                Element img = doc.select(selector).first();
                if (img != null) {
                    String src = img.attr("src");
                    if (isValidImage(src)) {
                        src = normalizeImageUrl(src, url);
                        System.out.println("    ✓ 文章圖片");
                        return src;
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("  圖片抓取失敗: " + e.getMessage());
        }
        
        System.out.println("  使用備用圖片");
        return getFallbackImage();
    }
    
    private boolean isValidImage(String url) {
        if (url == null || url.isEmpty()) return false;
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("logo") || lowerUrl.contains("icon") || lowerUrl.contains("avatar")) return false;
        if (lowerUrl.contains("1x1") || lowerUrl.contains("pixel") || lowerUrl.contains("blank")) return false;
        if (lowerUrl.contains("data:image")) return false;
        return lowerUrl.startsWith("http") && 
               (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".png") || 
                lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".webp") ||
                lowerUrl.contains("media") || lowerUrl.contains("image"));
    }
    
    private String normalizeImageUrl(String imgUrl, String pageUrl) {
        if (imgUrl == null) return getFallbackImage();
        if (imgUrl.startsWith("//")) {
            return "https:" + imgUrl;
        } else if (imgUrl.startsWith("/")) {
            try {
                java.net.URI uri = new java.net.URI(pageUrl);
                return uri.getScheme() + "://" + uri.getHost() + imgUrl;
            } catch (Exception e) {
                return imgUrl;
            }
        }
        return imgUrl;
    }
    
    // ==================== 錯誤處理和輔助方法 ====================
    
    private Map<String, String> createErrorNews(String source) {
        Map<String, String> errorNews = new HashMap<>();
        errorNews.put("title", "⚠️ " + source + " 暫時無法連線");
        errorNews.put("source", "系統備用");
        errorNews.put("link", "#");
        errorNews.put("image", getFallbackImage());
        errorNews.put("summary", "請稍後再試");
        return errorNews;
    }
    
    private String getFallbackImage() {
        // 使用你自訂的圖片
        return "default-image.jpg";
    }
    
    // ==================== 拼字檢查 ====================
    
    private void spellCheck(String word, HttpServletResponse response) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        if (word == null || word.trim().isEmpty()) {
            result.put("error", "請輸入單字");
            response.getWriter().print(gson.toJson(result));
            return;
        }
        
        word = word.trim().toLowerCase();
        
        try {
            String apiUrl = "https://api.dictionaryapi.dev/api/v2/entries/en/" + 
                            java.net.URLEncoder.encode(word, "UTF-8");
            
            String jsonResponse = Jsoup.connect(apiUrl)
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()
                    .body();
            
            JsonArray meanings = JsonParser.parseString(jsonResponse).getAsJsonArray();
            
            if (meanings.size() > 0) {
                JsonObject firstEntry = meanings.get(0).getAsJsonObject();
                String definition = "";
                String partOfSpeech = "";
                
                if (firstEntry.has("meanings")) {
                    JsonArray meaningArray = firstEntry.getAsJsonArray("meanings");
                    if (meaningArray.size() > 0) {
                        JsonObject meaning = meaningArray.get(0).getAsJsonObject();
                        if (meaning.has("partOfSpeech")) {
                            partOfSpeech = meaning.get("partOfSpeech").getAsString();
                        }
                        if (meaning.has("definitions")) {
                            JsonArray defs = meaning.getAsJsonArray("definitions");
                            if (defs.size() > 0) {
                                definition = defs.get(0).getAsJsonObject().get("definition").getAsString();
                            }
                        }
                    }
                }
                
                result.put("word", word);
                result.put("correct", true);
                result.put("message", "✅ 拼字正確！「" + word + "」是有效的英文單字");
                if (!partOfSpeech.isEmpty()) result.put("partOfSpeech", partOfSpeech);
                if (!definition.isEmpty()) result.put("definition", definition);
            } else {
                throw new Exception("No definition found");
            }
            
        } catch (Exception e) {
            result.put("word", word);
            result.put("correct", false);
            result.put("message", "❌ 找不到「" + word + "」，請檢查拼字");
            
            List<String> suggestions = new ArrayList<>();
            suggestions.add("請檢查拼字");
            result.put("suggestions", suggestions);
        }
        
        response.getWriter().print(gson.toJson(result));
    }
}