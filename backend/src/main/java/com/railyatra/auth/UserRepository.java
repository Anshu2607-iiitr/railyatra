package com.railyatra.auth;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository; public interface UserRepository extends JpaRepository<User,Long>{Optional<User> findByEmailIgnoreCase(String email);Optional<User> findByPhone(String phone);boolean existsByEmailIgnoreCase(String email);boolean existsByPhone(String phone);}
