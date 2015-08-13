/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.client.spring;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.Resource;
import org.cloudfoundry.client.v2.space.ListSpacesRequest;
import org.cloudfoundry.client.v2.space.ListSpacesResponse;
import org.cloudfoundry.client.v3.application.CreateApplicationRequest;
import org.cloudfoundry.client.v3.application.CreateApplicationResponse;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.core.env.StandardEnvironment;
import rx.Observable;
import rx.schedulers.Schedulers;

public final class IntegrationTest {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private volatile String application;

    private volatile CloudFoundryClient client;

    private volatile String space;

    @Before
    public void configure() throws Exception {
        RelaxedPropertyResolver resolver = new RelaxedPropertyResolver(new StandardEnvironment(), null);

        this.application = resolver.getRequiredProperty("test.application");
        this.space = resolver.getRequiredProperty("test.space");

        this.client = new SpringCloudFoundryClientBuilder()
                .withApi(resolver.getRequiredProperty("test.host"))
                .withCredentials(
                        resolver.getRequiredProperty("test.username"),
                        resolver.getRequiredProperty("test.password"))
                .build();
    }

    @Test
    public void test() {
        listSpaces()
                .flatMap(this::createApplication)
                .doOnError(exception -> {
                    this.logger.error("Error encountered: {}", exception.getMessage());
                })
                .toBlocking()
                .forEach(response -> {
                    this.logger.info("Name: {}", response.getName());
                    this.logger.info("Id:   {}", response.getId());

                    response.getLinks().entrySet().stream().forEach(entry -> {
                        this.logger.info("Link: {}/{}", entry.getKey(), entry.getValue().getHref());
                    });
                });
    }

    private Observable<ListSpacesResponse> listSpaces() {
        ListSpacesRequest request = new ListSpacesRequest()
                .filterByName(this.space);

        return this.client.space().list(request)
                .subscribeOn(Schedulers.io());
    }

    private Observable<CreateApplicationResponse> createApplication(ListSpacesResponse response) {
        Resource.Metadata metadata = response.getResources().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find space " + this.space))
                .getMetadata();

        CreateApplicationRequest request = new CreateApplicationRequest()
                .withSpaceId(metadata.getId())
                .withName(this.application);

        return this.client.application().create(request)
                .subscribeOn(Schedulers.io());
    }


}
