package com.enterprise.iam.auth.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.outbox.mapper.OutboxPersistenceMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;

final class AuthPersistenceTestMapperFactory {

    private AuthPersistenceTestMapperFactory() {
    }

    static AuthPersistenceMapper create(DataSource dataSource) {
        try {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setArgNameBasedConstructorAutoMapping(true);
            configuration.addMapper(AuthPersistenceMapper.class);

            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setConfiguration(configuration);
            factoryBean.setMapperLocations(
                    new ClassPathResource("mapper/AuthPersistenceMapper.xml"));
            SqlSessionFactory factory = factoryBean.getObject();
            if (factory == null) {
                throw new IllegalStateException("MyBatis session factory was not created");
            }
            return new SqlSessionTemplate(factory).getMapper(AuthPersistenceMapper.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create auth persistence mapper", exception);
        }
    }

    static OutboxPersistenceMapper createOutbox(DataSource dataSource) {
        try {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setArgNameBasedConstructorAutoMapping(true);
            configuration.addMapper(OutboxPersistenceMapper.class);

            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setConfiguration(configuration);
            factoryBean.setMapperLocations(
                    new ClassPathResource("mapper/OutboxPersistenceMapper.xml"));
            SqlSessionFactory factory = factoryBean.getObject();
            if (factory == null) {
                throw new IllegalStateException("MyBatis session factory was not created");
            }
            return new SqlSessionTemplate(factory).getMapper(OutboxPersistenceMapper.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create outbox persistence mapper", exception);
        }
    }
}
