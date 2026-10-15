package com.tfzhang.linuxback.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "tencent-cos")
    public COSClient cosClient(StorageProperties props) {
        StorageProperties.TencentCos cos = props.getTencentCos();
        if (cos == null
                || isBlank(cos.getSecretId())
                || isBlank(cos.getSecretKey())) {
            throw new IllegalStateException(
                    "app.storage.tencent-cos.secret-id and secret-key are required when type=tencent-cos");
        }
        COSCredentials cred = new BasicCOSCredentials(cos.getSecretId(), cos.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(cos.getRegion()));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        return new COSClient(cred, clientConfig);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
