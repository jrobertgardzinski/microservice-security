package com.jrobertgardzinski.security.application.feature.identity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.application.feature.support.InMemoryUserRepository;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.vo.DisplayName;
import com.jrobertgardzinski.security.system.identity.DisplayNames;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DisplayNamesSteps {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final DisplayNames displayNames = new DisplayNames(users);
    private final UserId nobody = UserId.random();
    private Map<UserId, DisplayName> answer;

    @Given("an account for {string}")
    public void aRegisteredUser(String email) {
        users.save(new User(Email.of(email), new HashedPassword("seed-hash")));
    }

    @When("the names behind the ids of {string} and {string} are looked up")
    public void theNamesBehindTwoIds(String first, String second) {
        answer = displayNames.of(List.of(idOf(first), idOf(second)));
    }

    @When("the names behind the id of {string} and an id nobody holds are looked up")
    public void theNamesBehindAnIdAndNobody(String email) {
        answer = displayNames.of(List.of(idOf(email), nobody));
    }

    @Then("{string} is shown as {string}")
    public void isShownAs(String email, String name) {
        assertEquals(new DisplayName(name), answer.get(idOf(email)));
    }

    @Then("the id nobody holds is not in the answer")
    public void nobodyIsNotInTheAnswer() {
        assertFalse(answer.containsKey(nobody));
    }

    private UserId idOf(String email) {
        return users.findBy(Email.of(email)).orElseThrow().id();
    }
}
