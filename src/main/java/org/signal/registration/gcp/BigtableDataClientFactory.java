/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.gcp;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Factory
@Requires(env = {Environment.GOOGLE_COMPUTE, Environment.CLI})
@Requires(property = "gcp.key-file-path")
@Requires(property = "gcp.project-id")
@Requires(property = "gcp.bigtable.instance-id")
public class BigtableDataClientFactory {

  @Singleton
  BigtableDataClient bigtableDataClient(@Value("${gcp.key-file-path}") final String keyFilePath,
      @Value("${gcp.project-id}") final String projectId,
      @Value("${gcp.bigtable.instance-id}") final String instanceId
      ) throws IOException {

    GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(new File(keyFilePath)));

    BigtableDataSettings bigtableDataSettings = BigtableDataSettings.newBuilder()
        .setProjectId(projectId)
        .setInstanceId(instanceId)
        .setCredentialsProvider(new CredentialsProvider() {
          @Override
          public Credentials getCredentials() throws IOException {
            return credentials;
          }
        })
        .build();
    BigtableDataClient bigtableDataClient = BigtableDataClient.create(bigtableDataSettings);

    return bigtableDataClient;
  }
}
