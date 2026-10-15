package com.tfzhang.newai.tools; // adjust to your actual package

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.Proxy;
import java.net.InetSocketAddress;

// Adjust this import to match whatever framework annotations you're using
// for @Tool and @P (e.g. LangChain4j's dev.langchain4j.agent.tool.Tool / .P)
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

@Slf4j
public class ListMyChapters {

    private static final Pattern ARTICLE_URL_PATTERN =
            Pattern.compile("https://daodog55\\.github\\.io/\\d{4}/\\d{2}/\\d{2}/([^/]+)/?$");

    /**
     * 获取 Linux 课程博客站点的所有章节标题
     *
     * @return 章节标题列表，若失败则返回错误信息
     */
    @Tool(name = "linuxCourseChapterList", value = """
            Retrieves the list of all chapter titles from the Linux course blog (daodog55.github.io).
            Use this tool when the user asks what chapters/lessons the Linux course covers.
            """
    )
    public String listChapters() {
        String url = "https://daodog55.github.io/";
        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(5000)
                    .get();
        } catch (IOException e) {
            log.error("get web error", e);
            return e.getMessage();
        }

//        Document doc;
//        try {
//            doc = Jsoup.connect(url)
//                    .userAgent("Mozilla/5.0")
//                    .timeout(8000)
//                    .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890)))
//                    .get();
//        } catch (IOException e) {
//            log.error("get web error", e);
//            return e.getMessage();
//        }

        Elements links = doc.select("a[href]");
        LinkedHashSet<String> titles = new LinkedHashSet<>();

        for (Element a : links) {
            String href = a.attr("abs:href");
            Matcher m = ARTICLE_URL_PATTERN.matcher(href);
            if (!m.matches()) continue;

            try {
                titles.add(URLDecoder.decode(m.group(1), StandardCharsets.UTF_8));
            } catch (Exception e) {
                titles.add(m.group(1));
            }
        }

        return titles.isEmpty() ? "未找到章节" : String.join("\n", titles);
    }
}