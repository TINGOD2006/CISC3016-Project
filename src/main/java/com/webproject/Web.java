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
    
    // 來源1：Hacker News
    try {
        List<Map<String, String>> hackerNews = crawlHackerNews();
        allNews.addAll(hackerNews);
        System.out.println("Hacker News 爬取完成: " + hackerNews.size() + " 則");
        Thread.sleep(REQUEST_DELAY_MS);
    } catch (Exception e) {
        System.err.println("Hacker News 失敗: " + e.getMessage());
        allNews.add(createErrorNews("Hacker News"));
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
    
    System.out.println("總共回傳: " + allNews.size() + " 則即時新聞");
    }
    
    // ==================== 1. Hacker News 爬蟲 ====================
    
    private List<Map<String, String>> crawlHackerNews() throws Exception {
        List<Map<String, String>> newsList = new ArrayList<>();
        
        String idsJson = Jsoup.connect("https://hacker-news.firebaseio.com/v0/topstories.json")
                .ignoreContentType(true)
                .timeout(10000)
                .execute()
                .body();
        
        JsonArray idsArray = JsonParser.parseString(idsJson).getAsJsonArray();
        
        for (int i = 0; i < idsArray.size() && newsList.size() < 5; i++) {
            int newsId = idsArray.get(i).getAsInt();
            String itemJson = Jsoup.connect("https://hacker-news.firebaseio.com/v0/item/" + newsId + ".json")
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()
                    .body();
            
            JsonObject item = JsonParser.parseString(itemJson).getAsJsonObject();
            String title = item.has("title") ? item.get("title").getAsString() : "無標題";
            String link = item.has("url") ? item.get("url").getAsString() : 
                          "https://news.ycombinator.com/item?id=" + newsId;
            
            String imageUrl = extractImageFromUrl(link);
            
            Map<String, String> news = new HashMap<>();
            news.put("title", title);
            news.put("source", "Hacker News 🔥");
            news.put("link", link);
            news.put("image", imageUrl);
            news.put("score", item.has("score") ? item.get("score").getAsString() : "0");
            news.put("summary", "Hacker News 熱門討論");
            
            newsList.add(news);
            Thread.sleep(500);
        }
        
        return newsList;
    }
    
    // ==================== 2. CNN 科技新聞爬蟲 ====================
    
    private List<Map<String, String>> crawlCNNNews() throws Exception {
        List<Map<String, String>> newsList = new ArrayList<>();
        String url = "http://rss.cnn.com/rss/edition_technology.rss";
        
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get();
        
        Elements items = doc.select("item");
        
        for (int i = 0; i < items.size() && newsList.size() < 5; i++) {
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
            news.put("score", "熱門");
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
        
        for (int i = 0; i < items.size() && newsList.size() < 5; i++) {
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
                    .timeout(10000)
                    .ignoreHttpErrors(true)
                    .get();
            
            // 1. Open Graph 圖片
            Element ogImage = doc.select("meta[property='og:image']").first();
            if (ogImage != null) {
                String img = ogImage.attr("content");
                if (isValidImage(img)) {
                    System.out.println("    ✓ OG圖片: " + img.substring(0, Math.min(80, img.length())));
                    return img;
                }
            }
            
            // 2. Twitter Card 圖片
            Element twitterImage = doc.select("meta[name='twitter:image']").first();
            if (twitterImage != null) {
                String img = twitterImage.attr("content");
                if (isValidImage(img)) {
                    System.out.println("    ✓ Twitter圖片: " + img.substring(0, Math.min(80, img.length())));
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
                        System.out.println("    ✓ 文章圖片: " + src.substring(0, Math.min(80, src.length())));
                        return src;
                    }
                }
            }
            
            // 4. 任何尺寸較大的圖片
            Elements allImgs = doc.select("img[src]");
            String bestImg = null;
            int maxSize = 0;
            for (Element img : allImgs) {
                String src = img.attr("src");
                if (isValidImage(src)) {
                    src = normalizeImageUrl(src, url);
                    int width = 0;
                    try {
                        width = Integer.parseInt(img.attr("width"));
                    } catch (NumberFormatException e) {
                        if (src.contains("large") || src.contains("1200") || src.contains("1024")) {
                            width = 800;
                        }
                    }
                    if (width > maxSize && width > 200) {
                        maxSize = width;
                        bestImg = src;
                    }
                }
            }
                    if (bestImg != null) {
            System.out.println("    ✓ 大尺寸圖片: " + bestImg.substring(0, Math.min(80, bestImg.length())));
            return bestImg;
        }
        
        // 5. 關鍵字備用圖片 - 已移除，直接使用你的自訂圖片
        
    } catch (Exception e) {
        System.err.println("  圖片抓取失敗: " + e.getMessage());
    }
    
    System.out.println("  使用自訂備用圖片");
    return getFallbackImage();
        }
    
    
    private boolean isValidImage(String url) {
        if (url == null || url.isEmpty()) return false;
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("logo") || lowerUrl.contains("icon") || lowerUrl.contains("avatar")) return false;
        if (lowerUrl.contains("1x1") || lowerUrl.contains("pixel") || lowerUrl.contains("blank")) return false;
        if (lowerUrl.contains("data:image")) return false;
        if (lowerUrl.contains("spacer") || lowerUrl.contains("placeholder")) return false;
        return lowerUrl.startsWith("http") && 
               (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".png") || 
                lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".webp") ||
                lowerUrl.contains("media") || lowerUrl.contains("image") ||
                lowerUrl.contains("storage") || lowerUrl.contains("upload"));
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
                return "https://www.google.com" + imgUrl;
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