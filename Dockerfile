# 使用官方 Tomcat 9 + JDK 11 映像
FROM tomcat:9.0-jdk11-openjdk-slim

# 刪除預設的 webapps 內容
RUN rm -rf /usr/local/tomcat/webapps/*

# 複製 WAR 檔案到 Tomcat 的 webapps 目錄
# 改名為 ROOT.war 可以讓網站直接在根目錄執行
COPY target/Web.war /usr/local/tomcat/webapps/ROOT.war

# 暴露 8080 連接埠（Tomcat 預設）
EXPOSE 8080

# 啟動 Tomcat
CMD ["catalina.sh", "run"]