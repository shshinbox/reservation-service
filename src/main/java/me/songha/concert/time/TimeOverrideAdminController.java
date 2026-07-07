package me.songha.concert.time;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/time-override")
public class TimeOverrideAdminController {

    private final TimeOverrideRepository repository;
    private final Environment environment;

    @PutMapping
    public void update(@RequestBody TimeOverrideSettings request) {
        if (isProd()) {
            throw new IllegalStateException("Time override is not allowed in the prod environment.");
        }

        repository.save(request);
    }

    @DeleteMapping
    public void reset() {
        if (isProd()) {
            throw new IllegalStateException("Time override is not allowed in the prod environment.");
        }

        repository.reset();
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
