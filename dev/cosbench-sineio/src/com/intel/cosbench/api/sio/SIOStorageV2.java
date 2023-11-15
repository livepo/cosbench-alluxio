/**

MIT License

Copyright (c) 2021-Present SineIO

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package com.intel.cosbench.api.sio;

import static com.intel.cosbench.client.sio.SIOConstants.*;

import java.io.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.ListIterator;
import java.util.ArrayList;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.utils.AttributeMap;

import com.intel.cosbench.api.storage.*;
import com.intel.cosbench.api.context.*;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;

public class SIOStorageV2 extends NoneStorage {

	private int timeout;

	private String accessKey;
	private String secretKey;
	private String endpoint;

	private S3Client client;

	private String storageClass;
	private int restoreDays;
	private long partSize; // Upload the file parts.
	private boolean noVerifySSL;
	private String awsRegion;

	private boolean pathStyleAccess;
	private int maxConnections;
	private String proxyHost;
	private String proxyPort;

	@Override
	public void init(Config config, Logger logger) {
		super.init(config, logger);

		timeout = config.getInt(CONN_TIMEOUT_KEY, CONN_TIMEOUT_DEFAULT);
		endpoint = config.get(ENDPOINT_KEY, ENDPOINT_DEFAULT);
		accessKey = config.get(AUTH_USERNAME_KEY, AUTH_USERNAME_DEFAULT);
		secretKey = config.get(AUTH_PASSWORD_KEY, AUTH_PASSWORD_DEFAULT);

		pathStyleAccess = config.getBoolean(PATH_STYLE_ACCESS_KEY, PATH_STYLE_ACCESS_DEFAULT);
		maxConnections = config.getInt(MAX_CONNECTIONS, MAX_CONNECTIONS_DEFAULT);

		proxyHost = config.get(PROXY_HOST_KEY, "");
		proxyPort = config.get(PROXY_PORT_KEY, "");

		// 2021.02.14
		// You can set storage_class to other value in storage part.
		storageClass = config.get(STORAGE_CLASS_KEY, STORAGE_CLASS_DEFAULT);

		// 2021.07.11
		// You can set restore_days to other value(int) in storage part.
		restoreDays = config.getInt(RESTORE_DAYS_KEY, RESTORE_DAYS_DEFAULT);

		// 2021.08.03
		// You can set part_size to other value in storage part.
		partSize = config.getLong(PART_SIZE_KEY, PART_SIZE_DEFAULT);

		// 2020.11.26
		// You can set no_verify_ssl to true in storage part to disable SSL checking.
		noVerifySSL = config.getBoolean(NO_VERIFY_SSL_KEY, NO_VERIFY_SSL_DEFAULT);

		// 2022.02.03
		// You can set region now, and default is us-east-1.
		awsRegion = config.get(REGION_KEY, REGION_DEFAULT);

		initClient();

	}

	private S3Client initClient() {
		logger.debug("initialize S3 client with storage config: {}", parms);

		AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);

		S3Configuration s3Config = S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build();

		ProxyConfiguration.Builder proxyConfig = ProxyConfiguration.builder();
		if ((!proxyHost.equals("")) && (!proxyPort.equals(""))) {
			proxyConfig.useSystemPropertyValues(Boolean.FALSE)
					.endpoint(URI.create("http://" + proxyHost + ":" + proxyPort));
		}

		ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder()
				// max connections.
				.maxConnections(maxConnections)
				// Set socket timeout for data to be transferred.
				.socketTimeout(Duration.ofMillis(timeout))
				// Set connection timeout for initially establishing a connection.
				.connectionTimeout(Duration.ofMillis(timeout))
				// disable expect continue for HTTP/1.1.
				.expectContinueEnabled(false).proxyConfiguration(proxyConfig.build());

		SdkHttpClient httpClient;
		
		if (noVerifySSL) {
			httpClient = httpClientBuilder.buildWithDefaults(AttributeMap.builder()
					.put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, Boolean.TRUE).build());
		} else {
			httpClient = httpClientBuilder.build();
		}
		
		client = S3Client.builder().credentialsProvider(StaticCredentialsProvider.create(awsCreds))
				.endpointOverride(URI.create(endpoint))
				// solve issue: https://github.com/sine-io/cosbench-sineio/issues/3
				.region(Region.of(awsRegion)).serviceConfiguration(s3Config).httpClient(httpClient).build();

		logger.debug("S3 client has been initialized");

		return client;
	}

	@Override
	public void setAuthContext(AuthContext info) {
		super.setAuthContext(info);
	}

	@Override
	public void dispose() {
		super.dispose();
		client = null;
	}

	@Override
	public InputStream getObject(String container, String object, Config config) {
		super.getObject(container, object, config);
		InputStream stream = null;
		try {
			GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(container).key(object).build();
			ResponseBytes<GetObjectResponse> objectBytes = client.getObjectAsBytes(getObjectRequest);

			stream = objectBytes.asInputStream();

		} catch (AwsServiceException ase) {
			throw new StorageException(ase);
		} catch (SdkClientException sce) {
			throw new StorageTimeoutException(sce);
		}

		return stream;
	}

	@Override
	public void restoreObject(String container, String object, Config config) {
		super.restoreObject(container, object, config);
		try {
			// Create and submit a request to restore an object from Glacier/Deep Archive
			// for some days.
			RestoreRequest restoreRequest = RestoreRequest.builder().days(restoreDays).build();

			RestoreObjectRequest restoreObjectRequest = RestoreObjectRequest.builder().bucket(container).key(object)
					.restoreRequest(restoreRequest).build();

			client.restoreObject(restoreObjectRequest);

		} catch (Exception e) {
			throw new StorageException(e);
		}
	}

	@Override
	public void createContainer(String container, Config config) {
		super.createContainer(container, config);
		try {
			container = container.split("/")[0];

			CreateBucketRequest createBucketRequest = CreateBucketRequest.builder().bucket(container).build();
			client.createBucket(createBucketRequest);

		} catch (Exception e) {
			throw new StorageException(e);
		}
	}

	@Override
	public void createObject(String container, String object, InputStream data, long length, Config config) {
		super.createObject(container, object, data, length, config);

		PutObjectRequest putObjectRequest = PutObjectRequest.builder().contentLength(length)
				.contentType("application/octet-stream").storageClass(storageClass).bucket(container).key(object)
				.build();

		RequestBody requestBody = RequestBody.fromInputStream(data, length);

		try {
			client.putObject(putObjectRequest, requestBody);
		} catch (AwsServiceException ase) {
			throw new StorageException(ase);
		} catch (SdkClientException sce) {
			throw new StorageTimeoutException(sce);
		}
	}

	// 2021.8.3 updated, sine.
	@Override
	public void createMultipartObject(String container, String object, InputStream data, long length, Config config) {
		super.createMultipartObject(container, object, data, length, config);

//		Map<String, String> metadata = new HashMap<>();
//      metadata.put("Content-Length", Long.toString(length));

		CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
				.contentType("application/octet-stream").storageClass(storageClass).bucket(container).key(object)
//				.overrideConfiguration(AwsRequestOverrideConfiguration.builder().putHeader("Content-Length", Long.toString(length)).build())
//				.metadata(metadata)
				.build();

		// Create a list of ETag objects. You retrieve ETags for each object part
		// uploaded,
		// then, after each individual part has been uploaded, pass the list of ETags to
		// the request to complete the upload.
		List<CompletedPart> partETags = new ArrayList<CompletedPart>();

		try {
			CreateMultipartUploadResponse response = client.createMultipartUpload(createMultipartUploadRequest);
			String uploadId = response.uploadId();

			long position = 0;
			long tempPartSize; // avoid to change the partSize, bug fix:#25

			for (int i = 1; position < length; i++) {
				// Because the last part could be less than 5 MiB, adjust the part size as
				// needed.
				tempPartSize = Math.min(partSize, (length - position));

				// Create the request to upload a part.
				UploadPartRequest uploadRequest = UploadPartRequest.builder().contentLength(tempPartSize) // TODO: needed?
						.bucket(container).key(object).uploadId(uploadId).partNumber(i).build();

				// Upload the part and add the response's ETag to our list.
				RequestBody requestBody = RequestBody.fromInputStream(data, tempPartSize);
				UploadPartResponse uploadPartResponse = client.uploadPart(uploadRequest, requestBody);

				CompletedPart tempPart = CompletedPart.builder().partNumber(i).eTag(uploadPartResponse.eTag()).build();
				partETags.add(tempPart);

				position += tempPartSize;
			}

			CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder().parts(partETags)
					.build();

			// Complete the multipart upload.
			CompleteMultipartUploadRequest compRequest = CompleteMultipartUploadRequest.builder().bucket(container)
					.key(object).uploadId(uploadId).multipartUpload(completedMultipartUpload).build();
			client.completeMultipartUpload(compRequest);

		} catch (AwsServiceException ase) {
			throw new StorageException(ase);
		} catch (SdkClientException sce) {
			throw new StorageTimeoutException(sce);
		}
	}

	@Override
	public void deleteContainer(String container, Config config) {
		super.deleteContainer(container, config);
		try {
			container = container.split("/")[0];

			DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(container).build();

			client.deleteBucket(deleteBucketRequest);

		} catch (Exception e) {
			throw new StorageException(e);
		}
	}

	@Override
	public void deleteObject(String container, String object, Config config) {
		super.deleteObject(container, object, config);
		try {
			DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(container).key(object)
					.build();

			client.deleteObject(deleteObjectRequest);
		} catch (Exception e) {
			throw new StorageException(e);
		}
	}
	
	@Override
	public InputStream getList(String container, String object, Config config) {
		super.getList(container, object, config);
		InputStream stream = null;
		
		try {
			StringBuilder sb = new StringBuilder();
			
			ListObjectsV2Request req = ListObjectsV2Request.builder().bucket(container).prefix(object).maxKeys(1000)
					.build();
			ListObjectsV2Response result = client.listObjectsV2(req);
			
			List<S3Object> objects = result.contents();
			
            for (ListIterator<S3Object> iterVals = objects.listIterator(); iterVals.hasNext(); ) {
                S3Object obj = (S3Object) iterVals.next();
                sb.append(obj.key()).append("\n");
             }
            
			stream = new ByteArrayInputStream(sb.toString().getBytes());
		} catch (Exception e) {
			throw new StorageException(e);
		}

		return stream;
	}

	@Override
	public void headObject(String container, String object, Config config) {
		super.headObject(container, object, config);

		HeadObjectRequest headObjectRequest = HeadObjectRequest.builder().bucket(container).key(object).build();

		try {
			client.headObject(headObjectRequest);

		} catch (Exception e) {
			throw new StorageException(e);
		}
	}
}
