package com.intel.cosbench.api.AlluxioStor;

import static com.intel.cosbench.client.AlluxioStor.AlluxioConstants.*;

import java.io.*;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.BufferedHttpEntity;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;

import com.intel.cosbench.api.storage.*;
import com.intel.cosbench.api.context.*;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;

public class AlluxioStorage extends NoneStorage {
	private Timeout timeout;
	
    private String endpoint;
    private String authorization;
    
    private CloseableHttpClient client;

    @Override
    public void init(Config config, Logger logger) {
    	super.init(config, logger);
    	
    	timeout = Timeout.ofSeconds(config.getLong(CONN_TIMEOUT_KEY, CONN_TIMEOUT_DEFAULT));
    	
    	endpoint = config.get(ENDPOINT_KEY, ENDPOINT_DEFAULT);
    	if (endpoint.endsWith("/")) {
    		endpoint = endpoint.substring(0, endpoint.length()-1);
    	}
    	authorization = config.get(AUTHORIZATION_KEY, "");
    	authorization = "AWS4-ECDSA-P256-SHA256 Credential=GQMJ1HOQXKD66YTVOSA9/20230814/s3/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date;x-amz-region-set, Signature=30440220496ac29ab19d8c6cbfd2133c85542961dd3888bf0a2cc92fdd6f57ba3c39e66502200de5de3eb5a95f155fd6d9b941a74768da0c68f065b06c553408bb2e8bbdc01e";
    	RequestConfig requestConfig = RequestConfig.custom()
    			.setConnectTimeout(timeout)
    			.setResponseTimeout(timeout)
    			.setConnectionRequestTimeout(timeout).build();
   
    	client = org.apache.hc.client5.http.impl.classic.HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
        
        logger.debug("alluxio client has been initialized");
    }
    
    @Override
    public void setAuthContext(AuthContext info) {
        super.setAuthContext(info);
//        try {
//        	client = (AmazonS3)info.get(S3CLIENT_KEY);
//            logger.debug("s3client=" + client);
//        } catch (Exception e) {
//            throw new StorageException(e);
//        }
    }

    @Override
    public void dispose() {
        super.dispose();
        client = null;
    }

	@Override
    public InputStream getObject(String container, String object, Config config) {
        super.getObject(container, object, config);
        InputStream stream;
        try {
        	String requestUrl = endpoint + "/api/v1/s3/" + container + "/" + object;
        	final HttpGet request = new HttpGet(requestUrl);
            request.setHeader(HttpHeaders.AUTHORIZATION, authorization);
            
            CloseableHttpResponse response = (CloseableHttpResponse) client.execute(request);
            final HttpEntity entity = response.getEntity();
            stream = entity.getContent();
            
        } catch (Exception e) {
            throw new StorageException(e);
        }
        return stream;
    }

    @Override
    public void createContainer(String container, Config config) {
        super.createContainer(container, config);
        String requestUrl = endpoint + "/api/v1/s3/" + container;
        try {
        	final HttpPut request = new HttpPut(requestUrl);
        	request.setHeader(HttpHeaders.AUTHORIZATION, authorization); 
            CloseableHttpResponse response = (CloseableHttpResponse) client.execute(request);
            logger.info(response.toString());
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

	@Override
    public void createObject(String container, String object, InputStream data,
            long length, Config config) {
        super.createObject(container, object, data, length, config);
        String requestUrl = endpoint + "/api/v1/s3/" + container  + "/" + object;
        try {
        	final HttpPut request = new HttpPut(requestUrl);
        	request.setHeader(HttpHeaders.AUTHORIZATION, authorization); 
        	// request.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(length));
        	// request.setHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
        	EntityBuilder builder = EntityBuilder.create();
        	builder.setContentType(ContentType.APPLICATION_OCTET_STREAM);
        	builder.setStream(data);
        	BufferedHttpEntity entity = new BufferedHttpEntity(builder.build());
        	request.setEntity(entity);
        	CloseableHttpResponse response = (CloseableHttpResponse) client.execute(request);
            logger.info(response.toString());
        } catch (Exception e) {
        	logger.info("put error&&&", e);
        	e.printStackTrace();
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteContainer(String container, Config config) {
        super.deleteContainer(container, config);
        String requestUrl = endpoint + "/api/v1/s3/" + container;
        try {
        	final HttpDelete request = new HttpDelete(requestUrl);
        	request.setHeader(HttpHeaders.AUTHORIZATION, authorization);
        	CloseableHttpResponse response = (CloseableHttpResponse) client.execute(request);
            logger.info(response.toString());
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteObject(String container, String object, Config config) {
        super.deleteObject(container, object, config);
        String requestUrl = endpoint + "/api/v1/s3/" + container  + "/" + object;
        try {
        	final HttpDelete request = new HttpDelete(requestUrl);
        	request.setHeader(HttpHeaders.AUTHORIZATION, authorization);
        	CloseableHttpResponse response = (CloseableHttpResponse) client.execute(request);
            logger.info(response.toString());
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

}
