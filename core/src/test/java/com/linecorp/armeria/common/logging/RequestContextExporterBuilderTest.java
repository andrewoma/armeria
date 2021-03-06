/*
 * Copyright 2019 LINE Corporation
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

package com.linecorp.armeria.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RequestContextExporterBuilderTest {

    @Test
    void addBuiltInProperties() throws Exception {
        final RequestContextExporterBuilder builder = RequestContextExporter.builder();
        for (BuiltInProperty property : BuiltInProperty.values()) {
            builder.addKeyPattern(property.key);
        }
        assertThat(builder.getBuiltIns()).containsExactly(BuiltInProperty.values());
        assertThat(builder.build().builtIns()).containsExactly(BuiltInProperty.values());
    }

    @Test
    void addWithoutWildcards() throws Exception {
        final RequestContextExporterBuilder builder = RequestContextExporter.builder();
        builder.addKeyPattern(BuiltInProperty.REMOTE_HOST.key);
        assertThat(builder.getBuiltIns()).containsExactly(BuiltInProperty.REMOTE_HOST);
        assertThat(builder.build().builtIns()).containsExactly(BuiltInProperty.REMOTE_HOST);
    }

    @Test
    void addWithWildcard() throws Exception {
        final RequestContextExporterBuilder builder = RequestContextExporter.builder();
        final BuiltInProperty[] expectedProperties =
                Arrays.stream(BuiltInProperty.values())
                      .filter(p -> p.key.startsWith("req."))
                      .toArray(BuiltInProperty[]::new);
        builder.addKeyPattern("req.*");
        assertThat(builder.getBuiltIns()).containsExactly(expectedProperties);
        assertThat(builder.build().builtIns()).containsExactly(expectedProperties);
    }

    @Test
    void addWithWildcards() throws Exception {
        final RequestContextExporterBuilder builder = RequestContextExporter.builder();
        final BuiltInProperty[] expectedProperties =
                Arrays.stream(BuiltInProperty.values())
                      .filter(p -> p.key.contains("rpc"))
                      .toArray(BuiltInProperty[]::new);
        builder.addKeyPattern("*rpc*");
        assertThat(builder.getBuiltIns()).containsExactly(expectedProperties);
        assertThat(builder.build().builtIns()).containsExactly(expectedProperties);
    }

    @Test
    void addAttrWithWildcard() throws Exception {
        final RequestContextExporterBuilder builder = RequestContextExporter.builder();
        builder.addKeyPattern("attrs.*");
        builder.addKeyPattern("attrs.my_attrs:MyAttribute");
        assertThat(1).isEqualTo(builder.getAttributes().size());
        assertThat(1).isEqualTo(builder.build().attributes().size());
    }
}
