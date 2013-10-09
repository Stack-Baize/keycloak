package org.keycloak.services.resources;

import org.keycloak.SkeletonKeyContextResolver;
import org.keycloak.models.mongo.keycloak.adapters.MongoDBSessionFactory;
import org.keycloak.services.managers.TokenManager;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.picketlink.PicketlinkKeycloakSession;
import org.keycloak.models.picketlink.PicketlinkKeycloakSessionFactory;
import org.keycloak.models.picketlink.mappings.ApplicationEntity;
import org.keycloak.models.picketlink.mappings.RealmEntity;
import org.keycloak.social.SocialRequestManager;
import org.picketlink.idm.PartitionManager;
import org.picketlink.idm.config.IdentityConfigurationBuilder;
import org.picketlink.idm.internal.DefaultPartitionManager;
import org.picketlink.idm.jpa.internal.JPAContextInitializer;
import org.picketlink.idm.jpa.model.sample.simple.*;

import javax.annotation.PreDestroy;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.servlet.ServletContext;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class KeycloakApplication extends Application {

    public static final String SESSION_FACTORY = "keycloak.sessionFactory";
    public static final String SESSION_FACTORY_PICKETLINK = "picketlink";
    public static final String SESSION_FACTORY_MONGO = "mongo";
    public static final String MONGO_HOST = "keycloak.mongodb.host";
    public static final String MONGO_PORT = "keycloak.mongodb.port";
    public static final String MONGO_DB_NAME = "keycloak.mongodb.databaseName";
    public static final String MONGO_DROP_DB_ON_STARTUP = "keycloak.mongodb.dropDatabaseOnStartup";


    protected Set<Object> singletons = new HashSet<Object>();
    protected Set<Class<?>> classes = new HashSet<Class<?>>();

    protected KeycloakSessionFactory factory;

    public KeycloakApplication(@Context ServletContext context) {
        KeycloakSessionFactory f = createSessionFactory();
        this.factory = f;
        context.setAttribute(KeycloakSessionFactory.class.getName(), factory);
        //classes.add(KeycloakSessionCleanupFilter.class);

        TokenManager tokenManager = new TokenManager();

        singletons.add(new RealmsResource(tokenManager));
        singletons.add(new SocialResource(tokenManager, new SocialRequestManager()));
        classes.add(SkeletonKeyContextResolver.class);
        classes.add(SaasService.class);

    }

    protected KeycloakSessionFactory createSessionFactory() {
        return buildSessionFactory();
    }

    public static KeycloakSessionFactory buildSessionFactory() {
        String sessionFactoryType = System.getProperty(SESSION_FACTORY, SESSION_FACTORY_PICKETLINK);
        if (SESSION_FACTORY_MONGO.equals(sessionFactoryType)) {
            return buildMongoDBSessionFactory();
        } else {
            return buildPicketlinkSessionFactory();
        }
    }

    private static KeycloakSessionFactory buildPicketlinkSessionFactory() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("keycloak-identity-store");
        return new PicketlinkKeycloakSessionFactory(emf, buildPartitionManager());
    }

    private static KeycloakSessionFactory buildMongoDBSessionFactory() {
        String host = System.getProperty(MONGO_HOST, "localhost");
        int port = Integer.parseInt(System.getProperty(MONGO_PORT, "27017"));
        String dbName = System.getProperty(MONGO_DB_NAME, "keycloak");
        boolean dropDatabaseOnStartup = Boolean.parseBoolean(System.getProperty(MONGO_DROP_DB_ON_STARTUP, "true"));
        return new MongoDBSessionFactory(host, port, dbName, dropDatabaseOnStartup);
    }

    public KeycloakSessionFactory getFactory() {
        return factory;
    }

    @PreDestroy
    public void destroy() {
        factory.close();
    }

    public PartitionManager createPartitionManager() {
        return buildPartitionManager();
    }

    public static PartitionManager buildPartitionManager() {
        IdentityConfigurationBuilder builder = new IdentityConfigurationBuilder();

        builder
                .named("KEYCLOAK_JPA_CONFIG")
                .stores()
                .jpa()
                .mappedEntity(
                        AttributedTypeEntity.class,
                        AccountTypeEntity.class,
                        RoleTypeEntity.class,
                        GroupTypeEntity.class,
                        IdentityTypeEntity.class,
                        RelationshipTypeEntity.class,
                        RelationshipIdentityTypeEntity.class,
                        PartitionTypeEntity.class,
                        PasswordCredentialTypeEntity.class,
                        DigestCredentialTypeEntity.class,
                        X509CredentialTypeEntity.class,
                        OTPCredentialTypeEntity.class,
                        AttributeTypeEntity.class,
                        RealmEntity.class,
                        ApplicationEntity.class
                )
                .supportGlobalRelationship(org.picketlink.idm.model.Relationship.class)
                .addContextInitializer(new JPAContextInitializer(null) {
                    @Override
                    public EntityManager getEntityManager() {
                        return PicketlinkKeycloakSession.currentEntityManager.get();
                    }
                })
                .supportAllFeatures();

        DefaultPartitionManager partitionManager = new DefaultPartitionManager(builder.buildAll());
        return partitionManager;
    }


    @Override
    public Set<Class<?>> getClasses() {
        return classes;
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }

}
