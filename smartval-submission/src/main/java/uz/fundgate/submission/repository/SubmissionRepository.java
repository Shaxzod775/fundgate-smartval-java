package uz.fundgate.submission.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.fundgate.submission.entity.Submission;
import uz.fundgate.submission.entity.SubmissionStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findByFounderEmail(String email);

    List<Submission> findByStatus(SubmissionStatus status);

    Page<Submission> findByStartupNameContainingIgnoreCase(String name, Pageable pageable);

    long countByStatus(SubmissionStatus status);
}
