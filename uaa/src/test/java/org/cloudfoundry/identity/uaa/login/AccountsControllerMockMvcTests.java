package org.cloudfoundry.identity.uaa.login;

import com.dumbster.smtp.SimpleSmtpServer;
import com.dumbster.smtp.SmtpMessage;
import org.cloudfoundry.identity.uaa.authentication.Origin;
import org.cloudfoundry.identity.uaa.authentication.UaaPrincipal;
import org.cloudfoundry.identity.uaa.codestore.JdbcExpiringCodeStore;
import org.cloudfoundry.identity.uaa.login.test.MockMvcTestClient;
import org.cloudfoundry.identity.uaa.mock.InjectedMockContextTest;
import org.cloudfoundry.identity.uaa.mock.util.MockMvcUtils;
import org.cloudfoundry.identity.uaa.user.UaaAuthority;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.cloudfoundry.identity.uaa.util.SetServerNameRequestPostProcessor;
import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;
import static org.springframework.util.StringUtils.isEmpty;

public class AccountsControllerMockMvcTests extends InjectedMockContextTest {

    private static SimpleSmtpServer mailServer;
    private String userEmail;
    private MockMvcTestClient mockMvcTestClient;
    private MockMvcUtils mockMvcUtils;
    private JavaMailSender originalSender;
    private RandomValueStringGenerator generator = new RandomValueStringGenerator();

    @BeforeClass
    public static void startMailServer() throws Exception {
        mailServer = SimpleSmtpServer.start(2525);
    }

    @Before
    public void setUp() throws Exception {
        originalSender = getWebApplicationContext().getBean("emailService", EmailService.class).getMailSender();

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(2525);
        getWebApplicationContext().getBean("emailService", EmailService.class).setMailSender(mailSender);

        userEmail = "user" +new RandomValueStringGenerator().generate()+ "@example.com";
        Assert.assertNotNull(getWebApplicationContext().getBean("messageService"));

        mockMvcTestClient = new MockMvcTestClient(getMockMvc());

        for (Iterator i = mailServer.getReceivedEmail(); i.hasNext();) {
            i.next();
            i.remove();
        }
        mockMvcUtils = MockMvcUtils.utils();
    }

    @After
    public void restoreMailSender() {
        getWebApplicationContext().getBean("emailService", EmailService.class).setMailSender(originalSender);
    }

    @AfterClass
    public static void stopMailServer() throws Exception {
        if (mailServer!=null) {
            mailServer.stop();
        }
    }

    @Test
    public void testCreateActivationEmailPage() throws Exception {
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "oss");

        getMockMvc().perform(get("/create_account"))
                .andExpect(content().string(containsString("Create your account")))
                .andExpect(content().string(not(containsString("Pivotal ID"))));
    }

    @Test
    public void testCreateActivationEmailPageWithPivotalBrand() throws Exception {
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "pivotal");

        getMockMvc().perform(get("/create_account"))
            .andExpect(content().string(containsString("Create your Pivotal ID")))
            .andExpect(content().string(not(containsString("Create your account"))));
    }

    @Test
    public void testCreateActivationEmailPageWithinZone() throws Exception {
        String subdomain = generator.generate();
        mockMvcUtils.createOtherIdentityZone(subdomain, getMockMvc(), getWebApplicationContext());
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "pivotal");

        getMockMvc().perform(get("/create_account")
            .with(new SetServerNameRequestPostProcessor(subdomain + ".localhost")))
            .andExpect(content().string(containsString("Create your account")))
            .andExpect(content().string(not(containsString("Pivotal ID"))));
    }

    @Test
    public void testActivationEmailSentPage() throws Exception {
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "oss");

        getMockMvc().perform(get("/accounts/email_sent"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create your account")))
                .andExpect(xpath("//input[@disabled='disabled']/@value").string("Email successfully sent"))
                .andExpect(content().string(not(containsString("Pivotal ID"))));
    }

    @Test
    public void testActivationEmailSentPageWithPivotalBrand() throws Exception {
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "pivotal");

        getMockMvc().perform(get("/accounts/email_sent"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create your Pivotal ID")))
                .andExpect(xpath("//input[@disabled='disabled']/@value").string("Email successfully sent"))
                .andExpect(content().string(not(containsString("Create your account"))));
    }

    @Test
    public void testActivationEmailSentPageWithinZone() throws Exception {
        String subdomain = generator.generate();
        mockMvcUtils.createOtherIdentityZone(subdomain, getMockMvc(), getWebApplicationContext());
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "pivotal");

        getMockMvc().perform(get("/accounts/email_sent")
            .with(new SetServerNameRequestPostProcessor(subdomain + ".localhost")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Create your account")))
            .andExpect(xpath("//input[@disabled='disabled']/@value").string("Email successfully sent"))
            .andExpect(content().string(not(containsString("Pivotal ID"))));
    }

    @Test
    public void testAcceptInvitationEmailWithOssBrand() throws Exception {
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "oss");

        getMockMvc().perform(get(getAcceptInvitationLink()))
            .andExpect(content().string(containsString("Create your account")))
            .andExpect(content().string(not(containsString("Pivotal ID"))))
            .andExpect(content().string(not(containsString("Create Pivotal ID"))))
            .andExpect(content().string(containsString("Create account")));
    }

    @Test
    public void testAcceptInvitationEmailWithPivotalBrand() throws Exception {
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "pivotal");

        getMockMvc().perform(get(getAcceptInvitationLink()))
            .andExpect(content().string(containsString("Create your Pivotal ID")))
            .andExpect(content().string(containsString("Pivotal products")))
            .andExpect(content().string(not(containsString("Create your account"))))
            .andExpect(content().string(containsString("Create Pivotal ID")))
            .andExpect(content().string(not(containsString("Create account"))));
    }

    @Test
    public void testAcceptInvitationEmailWithinZone() throws Exception {
        String subdomain = generator.generate();
        mockMvcUtils.createOtherIdentityZone(subdomain, getMockMvc(), getWebApplicationContext());
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "pivotal");

        getMockMvc().perform(get(getAcceptInvitationLink())
            .with(new SetServerNameRequestPostProcessor(subdomain + ".localhost")))
            .andExpect(content().string(containsString("Create your account")))
            .andExpect(content().string(not(containsString("Pivotal ID"))))
            .andExpect(content().string(not(containsString("Create Pivotal ID"))))
            .andExpect(content().string(containsString("Create account")));
    }

    @Test
    public void testPageTitleWithOssBrand() throws Exception {
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "oss");

        getMockMvc().perform(get("/create_account"))
            .andExpect(content().string(containsString("<title>Cloud Foundry</title>")));
    }

    @Test
    public void testPageTitleWithPivotalBrand() throws Exception {
        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "pivotal");

        getMockMvc().perform(get("/create_account"))
            .andExpect(content().string(containsString("<title>Pivotal</title>")));
    }

    @Test
    public void testPageTitleWithinZone() throws Exception {
        String subdomain = generator.generate();
        IdentityZone zone = mockMvcUtils.createOtherIdentityZone(subdomain, getMockMvc(), getWebApplicationContext());

        ((MockEnvironment) getWebApplicationContext().getEnvironment()).setProperty("login.brand", "pivotal");

        getMockMvc().perform(get("/create_account")
            .with(new SetServerNameRequestPostProcessor(subdomain + ".localhost")))
            .andExpect(content().string(containsString("<title>" + zone.getName() + "</title>")));
    }


    @Test
    public void testCreatingAnAccount() throws Exception {
        PredictableGenerator generator = new PredictableGenerator();
        JdbcExpiringCodeStore store = getWebApplicationContext().getBean(JdbcExpiringCodeStore.class);
        store.setGenerator(generator);

        getMockMvc().perform(post("/create_account.do")
            .param("email", userEmail)
            .param("password", "secr3T")
            .param("password_confirmation", "secr3T"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("accounts/email_sent"));

        MvcResult mvcResult = getMockMvc().perform(get("/verify_user")
            .param("code", "test" + generator.counter.get()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("home"))
                .andReturn();

        SecurityContext securityContext = (SecurityContext) mvcResult.getRequest().getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication authentication = securityContext.getAuthentication();
        Assert.assertThat(authentication.getPrincipal(), instanceOf(UaaPrincipal.class));
        UaaPrincipal principal = (UaaPrincipal) authentication.getPrincipal();
        Assert.assertThat(principal.getEmail(), equalTo(userEmail));
        Assert.assertThat(principal.getOrigin(), equalTo(Origin.UAA));
    }

    @Test
    public void testCreatingAnAccountWithAnEmptyClientId() throws Exception {
        PredictableGenerator generator = new PredictableGenerator();
        JdbcExpiringCodeStore store = getWebApplicationContext().getBean(JdbcExpiringCodeStore.class);
        store.setGenerator(generator);

        getMockMvc().perform(post("/create_account.do")
            .param("email", userEmail)
            .param("password", "secr3T")
            .param("password_confirmation", "secr3T")
            .param("client_id", ""))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("accounts/email_sent"));

        MvcResult mvcResult = getMockMvc().perform(get("/verify_user")
            .param("code", "test" + generator.counter.get()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("home"))
                .andReturn();

        SecurityContext securityContext = (SecurityContext) mvcResult.getRequest().getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication authentication = securityContext.getAuthentication();
        Assert.assertThat(authentication.getPrincipal(), instanceOf(UaaPrincipal.class));
        UaaPrincipal principal = (UaaPrincipal) authentication.getPrincipal();
        Assert.assertThat(principal.getEmail(), equalTo(userEmail));
        Assert.assertThat(principal.getOrigin(), equalTo(Origin.UAA));
    }

    @Test
    public void testCreatingAnAccountWithClientRedirect() throws Exception {
        PredictableGenerator generator = new PredictableGenerator();
        JdbcExpiringCodeStore store = getWebApplicationContext().getBean(JdbcExpiringCodeStore.class);
        store.setGenerator(generator);

        getMockMvc().perform(post("/create_account.do")
            .param("email", userEmail)
            .param("password", "secr3T")
            .param("password_confirmation", "secr3T")
            .param("client_id", "app"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("accounts/email_sent"));

        Iterator receivedEmail = mailServer.getReceivedEmail();
        SmtpMessage message = (SmtpMessage) receivedEmail.next();
        assertTrue(message.getBody().contains("Cloud Foundry"));
        assertTrue(message.getHeaderValue("From").contains("Cloud Foundry"));

        MvcResult mvcResult = getMockMvc().perform(get("/verify_user")
            .param("code", "test" + generator.counter.get()))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("http://localhost:8080/app/"))
            .andReturn();

        SecurityContext securityContext = (SecurityContext) mvcResult.getRequest().getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication authentication = securityContext.getAuthentication();
        Assert.assertThat(authentication.getPrincipal(), instanceOf(UaaPrincipal.class));
        UaaPrincipal principal = (UaaPrincipal) authentication.getPrincipal();
        Assert.assertThat(principal.getEmail(), equalTo(userEmail));
        Assert.assertThat(principal.getOrigin(), equalTo(Origin.UAA));
    }

    @Test
    public void testCreatingAnAccountInAnotherZoneWithNoClientRedirect() throws Exception {
        String subdomain = "mysubdomain2";
        PredictableGenerator generator = new PredictableGenerator();
        JdbcExpiringCodeStore store = getWebApplicationContext().getBean(JdbcExpiringCodeStore.class);
        store.setGenerator(generator);

        IdentityZone identityZone = new IdentityZone();
        identityZone.setSubdomain(subdomain);
        identityZone.setName(subdomain+"zone");
        identityZone.setId(new RandomValueStringGenerator().generate());

        String zonesCreateToken = mockMvcTestClient.getOAuthAccessToken("identity", "identitysecret", "client_credentials", "zones.write");
        getMockMvc().perform(post("/identity-zones")
            .header("Authorization", "Bearer " + zonesCreateToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonUtils.writeValueAsString(identityZone)))
                .andExpect(status().isCreated());

        getMockMvc().perform(post("/create_account.do")
            .with(new SetServerNameRequestPostProcessor(subdomain + ".localhost"))
            .param("email", userEmail)
            .param("password", "secr3T")
            .param("password_confirmation", "secr3T"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("accounts/email_sent"));

        Iterator receivedEmail = mailServer.getReceivedEmail();
        SmtpMessage message = (SmtpMessage) receivedEmail.next();
        String link = mockMvcTestClient.extractLink(message.getBody());
        assertTrue(message.getBody().contains(subdomain+"zone"));
        assertTrue(message.getHeaderValue("From").contains(subdomain+"zone"));
        assertFalse(message.getBody().contains("Cloud Foundry"));
        assertFalse(message.getBody().contains("Pivotal"));
        assertFalse(isEmpty(link));
        assertTrue(link.contains(subdomain+".localhost"));

        MvcResult mvcResult = getMockMvc().perform(get("/verify_user")
            .param("code", "test" + generator.counter.get())
            .with(new SetServerNameRequestPostProcessor(subdomain + ".localhost")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("home"))
                .andReturn();

        SecurityContext securityContext = (SecurityContext) mvcResult.getRequest().getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication authentication = securityContext.getAuthentication();
        Assert.assertThat(authentication.getPrincipal(), instanceOf(UaaPrincipal.class));
        UaaPrincipal principal = (UaaPrincipal) authentication.getPrincipal();
        Assert.assertThat(principal.getEmail(), equalTo(userEmail));
        Assert.assertThat(principal.getOrigin(), equalTo(Origin.UAA));
    }

    @Test
    public void testCreatingAnAccountInAnotherZoneWithClientRedirect() throws Exception {
        String subdomain = "mysubdomain1";
        PredictableGenerator generator = new PredictableGenerator();
        JdbcExpiringCodeStore store = getWebApplicationContext().getBean(JdbcExpiringCodeStore.class);
        store.setGenerator(generator);

        IdentityZone identityZone = new IdentityZone();
        identityZone.setSubdomain(subdomain);
        identityZone.setName(subdomain);
        identityZone.setId(new RandomValueStringGenerator().generate());


        BaseClientDetails clientDetails = new BaseClientDetails();
        clientDetails.setClientId("myzoneclient");
        clientDetails.setClientSecret("myzoneclientsecret");
        clientDetails.setAuthorizedGrantTypes(Arrays.asList("client_credentials"));
        clientDetails.addAdditionalInformation("signup_redirect_url", "http://myzoneclient.example.com");

        mockMvcUtils.createOtherIdentityZone(subdomain, getMockMvc(), getWebApplicationContext(), clientDetails);


        getMockMvc().perform(post("/create_account.do")
            .with(new SetServerNameRequestPostProcessor(subdomain + ".localhost"))
            .param("email", userEmail)
            .param("password", "secr3T")
            .param("password_confirmation", "secr3T")
            .param("client_id", "myzoneclient"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("accounts/email_sent"));

        Iterator receivedEmail = mailServer.getReceivedEmail();
        SmtpMessage message = (SmtpMessage) receivedEmail.next();
        String link = mockMvcTestClient.extractLink(message.getBody());
        assertFalse(isEmpty(link));
        assertTrue(link.contains(subdomain+".localhost"));

        MvcResult mvcResult = getMockMvc().perform(get("/verify_user")
            .param("code", "test" + generator.counter.get())
            .with(new SetServerNameRequestPostProcessor(subdomain + ".localhost")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://myzoneclient.example.com"))
                .andReturn();

        SecurityContext securityContext = (SecurityContext) mvcResult.getRequest().getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication authentication = securityContext.getAuthentication();
        Assert.assertThat(authentication.getPrincipal(), instanceOf(UaaPrincipal.class));
        UaaPrincipal principal = (UaaPrincipal) authentication.getPrincipal();
        Assert.assertThat(principal.getEmail(), equalTo(userEmail));
        Assert.assertThat(principal.getOrigin(), equalTo(Origin.UAA));
    }

    public static class PredictableGenerator extends RandomValueStringGenerator {
        public AtomicInteger counter = new AtomicInteger(1);
        @Override
        public String generate() {
            return  "test"+counter.incrementAndGet();
        }
    }

    private String getAcceptInvitationLink() throws Exception {
        String email = generator.generate() + "@example.com";
        getMockMvc().perform(post("/invitations/new.do")
            .session(setupSecurityContext()).with(csrf())
            .param("email", email))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("sent"));

        Iterator receivedEmail = mailServer.getReceivedEmail();
        SmtpMessage message = (SmtpMessage) receivedEmail.next();
        return mockMvcTestClient.extractLink(message.getBody());
    }

    private MockHttpSession setupSecurityContext() {
        UaaPrincipal p = new UaaPrincipal("123", "marissa", "marissa@test.org", Origin.UAA, "", IdentityZoneHolder.get().getId());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(p, "", UaaAuthority.USER_AUTHORITIES);
        assertTrue(auth.isAuthenticated());
        InvitationsControllerTest.MockSecurityContext mockSecurityContext = new InvitationsControllerTest.MockSecurityContext(auth);
        SecurityContextHolder.setContext(mockSecurityContext);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            mockSecurityContext
        );
        return session;
    }
}
