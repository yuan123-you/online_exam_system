package com.onlineexam.service;

import com.onlineexam.StoreService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Settlement must still happen when the student closes the browser or loses connectivity. */
@Component
public class ExpiredSubmissionScheduler {
  private static final Logger log = LoggerFactory.getLogger(ExpiredSubmissionScheduler.class);
  private final StoreService storage;
  private final SubmissionService submissions;

  public ExpiredSubmissionScheduler(StoreService storage, SubmissionService submissions) {
    this.storage = storage;
    this.submissions = submissions;
  }

  @Scheduled(fixedDelayString = "${exam.expiry-check-delay-ms:5000}", initialDelayString = "${exam.expiry-check-delay-ms:5000}")
  public void settleExpiredSessions() {
    StoreService.Store store;
    try { store = storage.readStore(); }
    catch (RuntimeException failure) {
      log.warn("Cannot load expiry sweep; will retry: {}", failure.getClass().getSimpleName());
      return;
    }
    for (Map<String, Object> saved : store.submissions) {
      try {
        if (submissions.isExpired(store, saved)) submissions.settleExpiredSubmission(store, saved);
      } catch (ResponseStatusException conflict) {
        log.warn("Expiry settlement deferred for {}: {}", saved.get("id"), conflict.getStatusCode());
      } catch (RuntimeException failure) {
        log.warn("Expiry settlement failed; will retry: {}", failure.getClass().getSimpleName());
      }
    }
  }
}
