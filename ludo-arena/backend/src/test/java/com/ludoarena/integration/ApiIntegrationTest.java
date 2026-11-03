package com.ludoarena.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludoarena.model.Enums.Role;
import com.ludoarena.model.User;
import com.ludoarena.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end REST tests against a REAL MongoDB. Skipped unless TEST_MONGODB_URI is set, e.g.
 *   docker run -d -p 27017:27017 mongo:7
 *   TEST_MONGODB_URI=mongodb://localhost:27017/ludo_arena_test mvn test
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
class ApiIntegrationTest {

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("TEST_MONGODB_URI"));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository users;

    record Session(String id, String email, String token) {
    }

    private Session register(String label) throws Exception {
        String email = label + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("name", "Tester " + label, "email", email, "password", "Passw0rdOK"))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode data = om.readTree(r.getResponse().getContentAsString()).get("data");
        return new Session(data.get("user").get("id").asText(), email, data.get("accessToken").asText());
    }

    private static String bearer(Session s) {
        return "Bearer " + s.token();
    }

    @Test
    void registerLoginAndMeWorkAndNeverLeakThePasswordHash() throws Exception {
        Session s = register("alice");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", s.email(), "password", "Passw0rdOK"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        MvcResult me = mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(s)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(s.email())).andReturn();
        String body = me.getResponse().getContentAsString();
        assertTrue(!body.contains("passwordHash") && !body.contains("$2a$"));
    }

    @Test
    void duplicateEmailAndWeakPasswordAreRejected() throws Exception {
        Session s = register("dup");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("name", "Other", "email", s.email(), "password", "Passw0rdOK"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("name", "Weak", "email", "weak@example.com", "password", "short"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors.password").exists());
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/v1/wallet")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallet").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoutesAreRejectedForRegularUsersEvenWithAValidToken() throws Exception {
        Session user = register("plain");
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", bearer(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(user))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/users/" + user.id() + "/status").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleIsReadFromTheDatabaseSoPromotionTakesEffectImmediately() throws Exception {
        Session s = register("promoted");
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", bearer(s))).andExpect(status().isForbidden());
        User u = users.findById(s.id()).orElseThrow();
        u.role = Role.ADMIN;
        users.save(u);
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", bearer(s)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalUsers").isNumber());
    }

    @Test
    void suspendedUsersAreLockedOutImmediately() throws Exception {
        Session admin = register("boss");
        User a = users.findById(admin.id()).orElseThrow();
        a.role = Role.ADMIN;
        users.save(a);
        Session victim = register("victim");

        mvc.perform(put("/api/v1/admin/users/" + victim.id() + "/status").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\",\"reason\":\"test\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/wallet").header("Authorization", bearer(victim)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", victim.email(), "password", "Passw0rdOK"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/logs").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].action").value("USER_SUSPENDED"));
    }

    @Test
    void newWalletHasTheSignupBonusAndIsNonRedeemable() throws Exception {
        Session s = register("wallet");
        mvc.perform(get("/api/v1/wallet").header("Authorization", bearer(s)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coinBalance").value(500))
                .andExpect(jsonPath("$.data.currencyType").value("NON_REDEEMABLE"))
                .andExpect(jsonPath("$.data.redeemable").value(false));
        mvc.perform(get("/api/v1/wallet/transactions").header("Authorization", bearer(s)))
                .andExpect(jsonPath("$.data.items[0].type").value("SIGNUP_BONUS"))
                .andExpect(jsonPath("$.data.items[0].previousBalance").value(0))
                .andExpect(jsonPath("$.data.items[0].newBalance").value(500));
    }

    @Test
    void dailyBonusCanBeClaimedOnlyOncePerDay() throws Exception {
        Session s = register("daily");
        mvc.perform(post("/api/v1/wallet/daily-bonus").header("Authorization", bearer(s))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/wallet/daily-bonus").header("Authorization", bearer(s)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_CLAIMED"));
    }

    @Test
    void parallelPurchasesOfTheSameItemChargeExactlyOnce() throws Exception {
        Session s = register("buyer");
        JsonNode items = om.readTree(mvc.perform(get("/api/v1/store/items").header("Authorization", bearer(s)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        JsonNode cheapest = null;
        for (JsonNode i : items) if (cheapest == null || i.get("priceCoins").asLong() < cheapest.get("priceCoins").asLong()) cheapest = i;
        assertNotNull(cheapest);
        long price = cheapest.get("priceCoins").asLong();
        String itemId = cheapest.get("id").asText();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Callable<Integer> call = () -> mvc.perform(post("/api/v1/store/purchase").header("Authorization", bearer(s))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":\"" + itemId + "\"}"))
                    .andReturn().getResponse().getStatus();
            results.add(pool.submit(call));
        }
        int created = 0;
        for (Future<Integer> f : results) if (f.get() == 201) created++;
        pool.shutdown();

        assertTrue(created >= 1, "at least one purchase succeeds");
        mvc.perform(get("/api/v1/wallet").header("Authorization", bearer(s)))
                .andExpect(jsonPath("$.data.coinBalance").value(500 - price));
        mvc.perform(get("/api/v1/store/inventory").header("Authorization", bearer(s)))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/wallet/transactions?type=STORE_PURCHASE").header("Authorization", bearer(s)))
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    void purchasingWithoutEnoughCoinsFailsAndLeavesTheBalanceUntouched() throws Exception {
        Session s = register("poor");
        JsonNode items = om.readTree(mvc.perform(get("/api/v1/store/items").header("Authorization", bearer(s)))
                .andReturn().getResponse().getContentAsString()).get("data");
        String expensive = null;
        for (JsonNode i : items) if (i.get("priceCoins").asLong() > 500) expensive = i.get("id").asText();
        if (expensive == null) return; // catalogue has nothing above the signup bonus
        mvc.perform(post("/api/v1/store/purchase").header("Authorization", bearer(s)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + expensive + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INSUFFICIENT_COINS"));
        mvc.perform(get("/api/v1/wallet").header("Authorization", bearer(s))).andExpect(jsonPath("$.data.coinBalance").value(500));
    }

    @Test
    void paymentsAreDisabledByDefault() throws Exception {
        Session s = register("pay");
        mvc.perform(get("/api/v1/payments/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/payments/status").header("Authorization", bearer(s)))
                .andExpect(jsonPath("$.data.enabled").value(false));
        mvc.perform(post("/api/v1/payments/orders").header("Authorization", bearer(s)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageId\":\"coins_500\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PAYMENTS_DISABLED"));
        mvc.perform(post("/api/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void roomFlowStartsAMatchThatOnlyItsPlayersCanRead() throws Exception {
        Session host = register("host");
        Session guest = register("guest");
        Session outsider = register("outsider");

        String roomId = om.readTree(mvc.perform(post("/api/v1/rooms").header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test room\",\"maxPlayers\":2,\"isPrivate\":false}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("data").get("id").asText();

        // one active room per user
        mvc.perform(post("/api/v1/rooms").header("Authorization", bearer(host)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second room\",\"maxPlayers\":2,\"isPrivate\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_IN_ROOM"));

        mvc.perform(post("/api/v1/rooms/" + roomId + "/join").header("Authorization", bearer(guest))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/rooms/" + roomId + "/join").header("Authorization", bearer(outsider)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ROOM_FULL"));

        mvc.perform(post("/api/v1/rooms/" + roomId + "/ready").header("Authorization", bearer(host))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ready\":true}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/rooms/" + roomId + "/ready").header("Authorization", bearer(guest))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ready\":true}")).andExpect(status().isOk());

        JsonNode room = om.readTree(mvc.perform(get("/api/v1/rooms/" + roomId).header("Authorization", bearer(host)))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertEquals("IN_PROGRESS", room.get("status").asText());
        String matchId = room.get("matchId").asText();

        mvc.perform(get("/api/v1/matches/" + matchId).header("Authorization", bearer(host)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.players.length()").value(2))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/matches/" + matchId).header("Authorization", bearer(outsider))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/matches/" + matchId + "/events").header("Authorization", bearer(guest)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].type").value("MATCH_STARTED"));
    }

    @Test
    void globalErrorsUseTheStandardEnvelope() throws Exception {
        Session s = register("errors");
        mvc.perform(get("/api/v1/rooms/64b7f8f8f8f8f8f8f8f8f8f8").header("Authorization", bearer(s)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_FOUND"));
        mvc.perform(post("/api/v1/rooms").header("Authorization", bearer(s)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
