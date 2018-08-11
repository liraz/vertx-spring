package io.vertx.ext.spring;

import java.util.List;

public class TestJsonObject {
    private Long id;
    private String name;
    private List<TestAnimal> animals;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TestAnimal> getAnimals() {
        return animals;
    }

    public void setAnimals(List<TestAnimal> animals) {
        this.animals = animals;
    }
}
