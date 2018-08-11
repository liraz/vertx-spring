package io.vertx.ext.spring;

public class DogAnimal extends TestJsonObject implements TestAnimal {

    public DogAnimal() {
        this.setName("fluffy");
    }

    @Override
    public String speak() {
        return "woof!";
    }
}
