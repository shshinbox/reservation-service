package me.songha.concert.time;

public interface TimeOverrideRepository {

    TimeOverrideSettings get();

    void save(TimeOverrideSettings config);

    void reset();
}
