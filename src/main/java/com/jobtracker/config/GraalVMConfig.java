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
            Arrays.asList(
                    "io.jsonwebtoken.Jwts$SIG",
                    "io.jsonwebtoken.Jwts$KEY",
                    "io.jsonwebtoken.Jwts$ENC",
                    "io.jsonwebtoken.impl.DefaultJwtBuilder",
                    "io.jsonwebtoken.impl.DefaultJwtParserBuilder"
            ).forEach(className ->
                    hints.reflection().registerType(TypeReference.of(className),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.PUBLIC_FIELDS,
                            MemberCategory.DECLARED_FIELDS,
                            MemberCategory.INVOKE_PUBLIC_METHODS)
            );

            hints.reflection().registerType(
                    org.hibernate.id.uuid.UuidGenerator.class,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS
            );
        }
    }
}