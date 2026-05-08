package com.jobtracker.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.Arrays;

@Configuration
@ImportRuntimeHints(GraalVMConfig.UuidHints.class)
public class GraalVMConfig {

    static class UuidHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(
                org.hibernate.id.uuid.UuidGenerator.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS
            );

            Arrays.asList(
                    "io.jsonwebtoken.impl.DefaultJwtBuilder",
                    "io.jsonwebtoken.impl.DefaultJwtParserBuilder",
                    "io.jsonwebtoken.impl.DefaultHeader",
                    "io.jsonwebtoken.impl.DefaultClaims",
                    "io.jsonwebtoken.impl.security.StandardSecureDigestAlgorithms",
                    "io.jsonwebtoken.jackson.io.JacksonSerializer",
                    "io.jsonwebtoken.jackson.io.JacksonDeserializer"
            ).forEach(className ->
                    hints.reflection().registerType(TypeReference.of(className),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
            );
        }
    }
}