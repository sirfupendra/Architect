package com.parser.Architect.Repositories;

import com.parser.Architect.Entites.CompletedArchitectRequests;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletedArchitectRequestsRepo extends JpaRepository<CompletedArchitectRequests,Long> {
}
