package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.application.support.Transactions;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

@Configuration
public class MongoConfiguration {

    /** Named so that {@code @Transactional(transactionManager = Transactions.MONGO)} picks it explicitly. */
    @Bean(Transactions.MONGO)
    MongoTransactionManager aiAssistantMongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    /**
     * Stops Spring Data from writing {@code _class}. Applied to the converter Spring Boot auto-configures, so its
     * custom conversions and mapping context stay in place.
     */
    @Bean
    static BeanPostProcessor omitTypeKeyPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof MappingMongoConverter converter) {
                    converter.setTypeMapper(new DefaultMongoTypeMapper(null));
                }
                return bean;
            }
        };
    }
}
