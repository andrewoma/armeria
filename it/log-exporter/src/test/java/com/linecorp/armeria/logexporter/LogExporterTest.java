package com.linecorp.armeria.logexporter;

/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import com.google.protobuf.ByteString;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientOptions;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.logging.LoggingClient;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.HttpServiceWithRoutes;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.server.logging.LoggingService;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.integration.Messages;
import io.grpc.testing.integration.TestServiceGrpc;
import io.netty.util.AttributeKey;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Test that attributes are logged with the request
 */
public class LogExporterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogExporterTest.class);

    static {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    private static final class TestService extends TestServiceGrpc.TestServiceImplBase {
        private static final Logger LOGGER = LoggerFactory.getLogger(TestService.class);

        private final WebClient client = WebClient.builder("https://google.com")
                .decorator(LoggingClient.newDecorator())
                .build();

        @Override
        public void unaryCall(Messages.SimpleRequest request, StreamObserver<Messages.SimpleResponse> responseObserver) {
            LOGGER.info("In service");

            try {
                client.get("/").aggregate().thenAccept(response -> System.out.println(response.status())).get();
            } catch (Exception e) {
                e.printStackTrace();
            }

            responseObserver.onNext(Messages.SimpleResponse.newBuilder()
                    .setPayload(Messages.Payload.newBuilder()
                            .setBody(ByteString.copyFrom("Foo", StandardCharsets.UTF_8))
                            .build())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @ClassRule
    public static final ServerRule server = new ServerRule() {

        private final HttpServiceWithRoutes grpcService =
                GrpcService.builder()
                        .addService(ProtoReflectionService.newInstance())
                        .addService(new TestService())
                        .useBlockingTaskExecutor(true)
                        .build();

        @Override
        protected void configure(ServerBuilder sb) {
            sb.https(new InetSocketAddress("127.0.0.1", 0));
            sb.tlsSelfSigned();
            sb.service(grpcService, RequestIdDecorator::new, LoggingService.newDecorator());
        }
    };

    private static final AttributeKey<String> REQUEST_ID_KEY = AttributeKey.valueOf("request_id_key");

    public static class RequestIdDecorator extends SimpleDecoratingHttpService {
        private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdDecorator.class);

        public RequestIdDecorator(HttpService delegate) {
            super(delegate);
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public HttpResponse serve(ServiceRequestContext ctx, @Nonnull HttpRequest req) throws Exception {
            String id = UUID.randomUUID().toString();
            ctx.setAttr(REQUEST_ID_KEY, id);
            LOGGER.info("Start request: {}", id);
            ctx.logBuilder().whenComplete().thenAccept(log -> {
                LOGGER.info("End request: {}", id);
            });
            return delegate().serve(ctx, req);
        }
    }

    @Test
    public void shouldLogAttributesOnAllLogsAssociatedWithRequest() {
        ClientFactory factory = ClientFactory.builder().tlsNoVerify().build();
        ClientOptions options = ClientOptions.builder().factory(factory).build();

        TestServiceGrpc.TestServiceBlockingStub client = Clients.builder("gproto+h1://127.0.0.1:" + server.httpsPort())
                .options(options)
                .build(TestServiceGrpc.TestServiceBlockingStub.class);

        LOGGER.info("Before request");
        // Every log line between "Before request" and "After request" should include `attrs.req_id=...`
        Messages.SimpleResponse response = client.unaryCall(Messages.SimpleRequest.newBuilder().build());
        LOGGER.info("After request: {}", response.getPayload().getBody().toString(StandardCharsets.UTF_8));
    }
}
