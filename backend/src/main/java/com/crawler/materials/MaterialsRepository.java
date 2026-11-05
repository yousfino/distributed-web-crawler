package com.crawler.materials;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.function.UnaryOperator;
import static com.crawler.materials.MaterialsModels.*;

@Repository
public class MaterialsRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public MaterialsRepository(JdbcTemplate jdbc,ObjectMapper mapper) {this.jdbc=jdbc;this.mapper=mapper;}
    public List<Inventory> all() {return jdbc.query("SELECT document FROM materials_inventories",(rs,row)->decode(rs.getString(1)));}
    public Inventory get(String id) {return load(id,false);}
    public Inventory create(Inventory inventory) {
        jdbc.update("INSERT INTO materials_inventories(id,document) VALUES (?,?)",inventory.id(),encode(inventory));return inventory;
    }
    @Transactional public Inventory update(String id,UnaryOperator<Inventory> change) {
        Inventory inventory=change.apply(load(id,true));
        jdbc.update("UPDATE materials_inventories SET document=? WHERE id=?",encode(inventory),id);return inventory;
    }
    private Inventory load(String id,boolean lock) {
        List<Inventory> values=jdbc.query("SELECT document FROM materials_inventories WHERE id=?"+(lock?" FOR UPDATE":""),(rs,row)->decode(rs.getString(1)),id);
        if(values.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Materials inventory not found");return values.get(0);
    }
    private Inventory decode(String value) {
        try{return mapper.readValue(value,Inventory.class);}catch(JsonProcessingException e){throw new IllegalStateException("Cannot read materials inventory",e);}
    }
    private String encode(Inventory value) {
        try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Cannot store materials inventory",e);}
    }
}
