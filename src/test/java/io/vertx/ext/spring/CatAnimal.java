package io.vertx.ext.spring;

public class CatAnimal extends TestJsonObject implements TestAnimal {

    public CatAnimal() {
        this.setName("mitsy");
    }

    @Override
    public String speak() {
        return "meow!";
    }
}
