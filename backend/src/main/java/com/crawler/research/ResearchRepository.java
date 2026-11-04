package com.crawler.research;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.function.UnaryOperator;
import static com.crawler.research.ResearchModels.*;

@Repository
public class ResearchRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public ResearchRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    public List<Project> all() {
        return jdbc.query("SELECT document FROM research_projects ORDER BY id", (rs, row) -> decode(rs.getString(1)));
    }
    public Project get(String id) { return load(id, false); }
    public Project create(Project project) {
        jdbc.update("INSERT INTO research_projects(id, document) VALUES (?, ?)", project.id(), encode(project));
        return project;
    }
    @Transactional
    public Project update(String id, UnaryOperator<Project> change) {
        Project updated = change.apply(load(id, true));
        jdbc.update("UPDATE research_projects SET document = ? WHERE id = ?", encode(updated), id);
        return updated;
    }
    private Project load(String id, boolean lock) {
        List<Project> projects=jdbc.query("SELECT document FROM research_projects WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> decode(rs.getString(1)), id);
        if (projects.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Research project not found");
        return projects.get(0);
    }
    private Project decode(String value) {
        try { return mapper.readValue(value, Project.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read stored research project", e); }
    }
    private String encode(Project project) {
        try { return mapper.writeValueAsString(project); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot store research project", e); }
    }
}
