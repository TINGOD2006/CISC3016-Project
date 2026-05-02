FROM tomcat:9.0-jdk11-openjdk-slim

# 移除預設的 webapps 內容
RUN rm -rf /usr/local/tomcat/webapps/*

# 複製 WAR 檔案並改名為 ROOT.war
COPY target/Web.war /usr/local/tomcat/webapps/ROOT.war

# 設定環境變數
ENV CATALINA_OPTS="-Dport=8080"

# 暴露連接埠
EXPOSE 8080

# 啟動 Tomcat
CMD ["catalina.sh", "run"]