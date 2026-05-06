package com.thomazcollet.domain.repository;

import com.thomazcollet.domain.model.Profile;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository {
    void save(Profile profile);
    Optional<Profile> findById(Long id);
    List<Profile> findAll();
    void delete(Long id);
}