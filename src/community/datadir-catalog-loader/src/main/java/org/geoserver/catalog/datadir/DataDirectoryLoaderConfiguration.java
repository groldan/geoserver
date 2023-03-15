package org.geoserver.catalog.datadir;

import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.ModuleStatusImpl;
import org.geoserver.security.GeoServerSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
@Conditional(value = DataDirectoryLoaderConfiguration.DataDirLoaderEnabledCondition.class)
public class DataDirectoryLoaderConfiguration {

    @Bean
    public DataDirectoryGeoServerLoader dataDirectoryGeoServerLoader(
            GeoServerResourceLoader resourceLoader, GeoServerSecurityManager securityManager) {
        return new DataDirectoryGeoServerLoader(resourceLoader, securityManager);
    }

    @Bean
    public ModuleStatusImpl moduleStatus() {
        ModuleStatusImpl module =
                new ModuleStatusImpl("gs-datadir-catalog-loader", "DataDirectory loader");
        module.setAvailable(true);
        module.setEnabled(true);
        return module;
    }

    static class DataDirLoaderEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String value = context.getEnvironment().getProperty("datadir.loader.enabled", "true");
            return Boolean.parseBoolean(value);
        }
    }
}
