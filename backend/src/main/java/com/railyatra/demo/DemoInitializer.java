package com.railyatra.demo;

import com.railyatra.auth.User;
import com.railyatra.auth.UserRepository;
import com.railyatra.railway.domain.FareRule;
import com.railyatra.railway.repository.*;
import com.railyatra.railway.service.CoachLayoutService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, isolated local demo profile only; no production bootstrap endpoint or default passwords. */
@Component @Profile("demo") @Order(100)
public class DemoInitializer implements ApplicationRunner {
    private final UserRepository users; private final PasswordEncoder encoder; private final JdbcTemplate jdbc;
    private final TrainRepository trains; private final TrainClassRepository classes; private final TrainStopRepository stops;
    private final CoachLayoutService layouts; private final FareRuleRepository fares;
    private final com.railyatra.railway.importer.RailwayDataImporter importer;
    private final com.railyatra.railway.importer.SyntheticRailwayDataGenerator generator;
    private final String adminPassword, userPassword;
    public DemoInitializer(UserRepository users,PasswordEncoder encoder,JdbcTemplate jdbc,TrainRepository trains,
        TrainClassRepository classes,TrainStopRepository stops,CoachLayoutService layouts,FareRuleRepository fares,
        com.railyatra.railway.importer.RailwayDataImporter importer,com.railyatra.railway.importer.SyntheticRailwayDataGenerator generator,
        @Value("${RAILYATRA_DEMO_ADMIN_PASSWORD:}") String adminPassword,@Value("${RAILYATRA_DEMO_USER_PASSWORD:}") String userPassword) {
        this.users=users;this.encoder=encoder;this.jdbc=jdbc;this.trains=trains;this.classes=classes;this.stops=stops;
        this.layouts=layouts;this.fares=fares;this.adminPassword=adminPassword;this.userPassword=userPassword;
        this.importer=importer;this.generator=generator;
    }
    @Override @Transactional public void run(ApplicationArguments arguments) {
        if(adminPassword.length()<12 || userPassword.length()<12) throw new IllegalStateException("Demo passwords must be supplied through environment variables and contain at least 12 characters. Use start-demo.ps1.");
        if(jdbc.queryForObject("SELECT completed_at FROM development_seed_runs WHERE seed_key='demo-synthetic-v1' FOR UPDATE",java.sql.Timestamp.class)==null) {
            var report=importer.importData(generator.generate(),"SYNTHETIC");
            jdbc.update("UPDATE development_seed_runs SET completed_at=CURRENT_TIMESTAMP WHERE seed_key='demo-synthetic-v1'");
            org.slf4j.LoggerFactory.getLogger(DemoInitializer.class).info("Demo import completed: elapsed={} records={}",report.elapsed(),report.records());
        }
        account("admin@demo.railyatra.invalid","Demo Administrator","9900000001",adminPassword,"ROLE_ADMIN");
        account("traveller@demo.railyatra.invalid","Demo Traveller","9900000002",userPassword,"ROLE_USER");
        for(String number:List.of("18625","18626")) {
            var train=trains.findByTrainNumber(number).orElse(null);
            if(train==null) continue; // An admin may have renumbered it; never recreate it on restart.
            var route=stops.findByTrainIdOrderByStopSequenceAsc(train.getId());
            for(var cls:classes.findByTrainIdOrderByClassCodeAsc(train.getId())) {
                if(!List.of("SL","3A").contains(cls.getClassCode())) continue;
                Long coachCount=jdbc.queryForObject("SELECT COUNT(*) FROM coaches WHERE train_class_id=?",Long.class,cls.getId());
                if(coachCount==0) layouts.createCoach(cls,cls.getClassCode().equals("SL")?"S1":"B1",1,cls.getClassCode()+"_STANDARD");
                Long fareCount=jdbc.queryForObject("SELECT COUNT(*) FROM fare_rules WHERE train_id=? AND train_class_id=?",Long.class,train.getId(),cls.getId());
                if(fareCount==0) fares.save(new FareRule(train,cls,route.getFirst().getStation(),route.getLast().getStation(),new BigDecimal("80.00"),new BigDecimal("1.25"),new BigDecimal("120.00")));
            }
        }
    }
    private void account(String email,String name,String phone,String password,String role) {
        String hash=encoder.encode(password);
        var user=users.findByEmailIgnoreCase(email).orElseGet(()->users.saveAndFlush(new User(name,email,phone,hash)));
        jdbc.update("UPDATE users SET password_hash=?, role=?, active=TRUE WHERE id=?",hash,role,user.getId());
    }
}
