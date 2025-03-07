package com.techempower;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.ServerOptions;
import io.jooby.hikari.HikariModule;
import io.jooby.json.JacksonModule;
import io.jooby.rocker.RockerModule;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import static io.jooby.ExecutionMode.EVENT_LOOP;
import static io.jooby.MediaType.JSON;

public class App extends Jooby {

  private static final String SELECT_WORLD = "select * from world where id=?";

  private static final int DB_ROWS = 10000;

  private static final String MESSAGE = "Hello, World!";

  private static final byte[] MESSAGE_BYTES = MESSAGE.getBytes(StandardCharsets.UTF_8);

  public static class Message {
    public final String message = MESSAGE;
  }

  {
    /** Server options (netty only): */
    setServerOptions(new ServerOptions()
        .setSingleLoop(true)
        .setDirectBuffers(true)
    );

    /** JSON: */
    install(new JacksonModule());
    ObjectMapper mapper = require(ObjectMapper.class);

    /** Database: */
    install(new HikariModule());
    DataSource ds = require(DataSource.class);

    /** Template engine: */
    install(new RockerModule());

    get("/plaintext", ctx ->
        ctx.send(MESSAGE_BYTES)
    );

    get("/json", ctx -> ctx
        .setResponseType(JSON)
        .send(mapper.writeValueAsBytes(new Message()))
    );

    /** Go blocking: */
    dispatch(() -> {

      /** Single query: */
      get("/db", ctx -> {
        Random rnd = ThreadLocalRandom.current();
        World result;
        try (Connection conn = ds.getConnection()) {
          int id = nextRandom(rnd);
          try (final PreparedStatement statement = conn.prepareStatement(SELECT_WORLD)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
              rs.next();
              result = new World(rs.getInt("id"), rs.getInt("randomNumber"));
            }
          }
        }
        return ctx
            .setResponseType(JSON)
            .send(mapper.writeValueAsBytes(result));
      });

      /** Multiple queries: */
      get("/queries", ctx -> {
        World[] result = new World[queries(ctx)];
        Random rnd = ThreadLocalRandom.current();
        try (Connection conn = ds.getConnection()) {
          for (int i = 0; i < result.length; i++) {
            int id = nextRandom(rnd);
            try (final PreparedStatement statement = conn.prepareStatement(SELECT_WORLD)) {
              statement.setInt(1, id);
              try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                result[i] = new World(rs.getInt("id"), rs.getInt("randomNumber"));
              }
            }
          }
        }
        return ctx
            .setResponseType(JSON)
            .send(mapper.writeValueAsBytes(result));
      });

      /** Updates: */
      get("/updates", ctx -> {
        World[] result = new World[queries(ctx)];
        Random rnd = ThreadLocalRandom.current();

        StringJoiner updateSql = new StringJoiner(
            ", ",
            "UPDATE world SET randomNumber = temp.randomNumber FROM (VALUES ",
            " ORDER BY 1) AS temp(id, randomNumber) WHERE temp.id = world.id");

        try (Connection connection = ds.getConnection()) {
          try (PreparedStatement statement = connection.prepareStatement(SELECT_WORLD)) {
            for (int i = 0; i < result.length; i++) {
              statement.setInt(1, nextRandom(rnd));
              try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                result[i] = new World(rs.getInt("id"), rs.getInt("randomNumber"));
              }
              // prepare update query
              updateSql.add("(?, ?)");
            }
          }

          try (PreparedStatement statement = connection.prepareStatement(updateSql.toString())) {
            int i = 0;
            for (World world : result) {
              world.randomNumber = nextRandom(rnd);
              statement.setInt(++i, world.id);
              statement.setInt(++i, world.randomNumber);
            }
            statement.executeUpdate();
          }
        }
        ctx.setResponseType(JSON);
        return ctx.send(mapper.writeValueAsBytes(result));
      });

      /** Fortunes: */
      get("/fortunes", ctx -> {
        List<Fortune> fortunes = new ArrayList<>();
        try (Connection connection = ds.getConnection()) {
          try (PreparedStatement stt = connection.prepareStatement("select * from fortune")) {
            try (ResultSet rs = stt.executeQuery()) {
              while (rs.next()) {
                fortunes.add(new Fortune(rs.getInt("id"), rs.getString("message")));
              }
            }
          }
        }
        fortunes.add(new Fortune(0, "Additional fortune added at request time."));
        Collections.sort(fortunes);

        /** render view: */
        return views.fortunes.template(fortunes);
      });
    });
  }

  public static void main(final String[] args) {
    runApp(args, EVENT_LOOP, App::new);
  }

  private final int nextRandom(Random rnd) {
    return rnd.nextInt(DB_ROWS) + 1;
  }

  private int queries(Context ctx) {
    String value = ctx.query("queries").value("");
    if (value.length() == 0) {
      return 1;
    }
    try {
      return Math.min(500, Math.max(1, Integer.parseInt(value)));
    } catch (NumberFormatException x) {
      return 1;
    }
  }
}
