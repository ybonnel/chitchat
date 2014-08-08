package fr.ybonnel.chitchat.model;

import org.jongo.marshall.jackson.oid.ObjectId;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Tweet {

    public String author;
    public String text;
    public long thread;
    public long createdAt;
}
