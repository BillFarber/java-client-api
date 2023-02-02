/*
 * Copyright (c) 2023 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.client.impl.okhttp;

import okhttp3.HttpUrl;

import javax.net.ssl.SSLContext;
import java.util.List;

/**
 * Extracted from OkHttpServices so that it can be easily unit-tested.
 */
public class HttpUrlBuilder {

	public static HttpUrl newBaseUrl(String host, int port, String basePath, SSLContext sslContext) {
		HttpUrl.Builder builder = new HttpUrl.Builder()
			.scheme(sslContext == null ? "http" : "https")
			.host(host)
			.port(port);

		if (basePath != null && basePath.trim().length() > 0) {
			if (basePath.startsWith("/")) {
				basePath = basePath.substring(1);
			}
			builder.addPathSegments(basePath);
		}
		builder.addPathSegment("v1");
		builder.addPathSegment("ping");
		return builder.build();
	}

	/**
	 * @param baseUrl expected to be produced via newBaseUrl, as Data Services uses the baseUrl in OkHttpServices to
	 *                then assemble its own base URL for DS calls
	 * @return
	 */
	public static HttpUrl newDataServicesBaseUri(HttpUrl baseUrl) {
		HttpUrl.Builder builder = new HttpUrl.Builder()
			.scheme(baseUrl.scheme())
			.host(baseUrl.host())
			.port(baseUrl.port());

		List<String> segments = baseUrl.pathSegments();
		int segmentCount = segments.size();

		// Expected to default to /v1/ping if no basePath is set; otherwise, it's basePath + /v1/ping
		if (segmentCount > 2) {
			segments = segments.subList(0, segmentCount - 2);
			for (String segment : segments) {
				builder = builder.addPathSegment(segment);
			}
		}

		builder = builder.addPathSegment("");
		return builder.build();
	}
}
