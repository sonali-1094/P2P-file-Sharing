package com.p2pfileshare.tracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2pfileshare.tracker.entity.*;
import com.p2pfileshare.tracker.repository.*;
import com.p2pfileshare.tracker.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class TrackerApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired SharedFileRepository files;
    @Autowired PeerRepository peers; @Autowired TransferRepository transfers; @Autowired PasswordEncoder passwords; @Autowired JwtService jwt;
    private String registerLogin(String email)throws Exception{
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\"pass-word-123\"}")).andExpect(status().isCreated());
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\"pass-word-123\"}")).andReturn().getResponse().getContentAsString()).get("token").asText();
    }
    private String fileBody(String name,String visibility,String hash){return "{\"name\":\""+name+"\",\"sizeBytes\":4,\"chunkSize\":4,\"fileHash\":\""+hash+"\",\"visibility\":\""+visibility+"\",\"chunks\":[{\"index\":0,\"sha256\":\""+"b".repeat(64)+"\",\"sizeBytes\":4}]}";}
    private long createFile(String token,String name,String visibility,String hash)throws Exception{return json.readTree(mvc.perform(post("/api/files").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(fileBody(name,visibility,hash))).andReturn().getResponse().getContentAsString()).get("id").asLong();}

    @Test void registrationLoginAndProtectedRoutes(){
        try {String token=registerLogin("flow@example.test");mvc.perform(get("/api/peers/online").header("Authorization","Bearer "+token)).andExpect(status().isOk());mvc.perform(get("/api/peers/online")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));}
        catch(Exception e){throw new AssertionError(e);}
    }
    @Test void duplicateEmailAndValidationReturnStructuredErrors()throws Exception{
        registerLogin("duplicate@example.test");mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"duplicate@example.test\",\"password\":\"pass-word-123\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"bad\",\"password\":\"short\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());
    }
    @Test void userDeniedAdminAndAdminAllowed()throws Exception{
        String user=registerLogin("userrole@example.test");User admin=users.saveAndFlush(new User("adminrole@example.test",passwords.encode("admin-password"),Role.ADMIN));String adminToken=jwt.issue(admin);
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
    }
    @Test void filesAreVisibleByPolicyAndRegistrationIsIdempotent()throws Exception{
        String a=registerLogin("owner-file@example.test"),b=registerLogin("reader-file@example.test");long id=createFile(a,"private.dat","PRIVATE","a".repeat(64));
        mvc.perform(get("/api/files/"+id).header("Authorization","Bearer "+b)).andExpect(status().isNotFound());
        mvc.perform(post("/api/files").header("Authorization","Bearer "+a).contentType(MediaType.APPLICATION_JSON).content(fileBody("private-renamed.dat","SHARED","a".repeat(64)))).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("private-renamed.dat"));
        mvc.perform(get("/api/files/"+id).header("Authorization","Bearer "+b)).andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("SHARED"));
        String bad=fileBody("bad.dat","PUBLIC","c".repeat(64)).replace("\"sizeBytes\":4","\"sizeBytes\":5");
        mvc.perform(post("/api/files").header("Authorization","Bearer "+b).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/files").header("Authorization","Bearer "+b).contentType(MediaType.APPLICATION_JSON).content(fileBody("bad-hash.dat","PUBLIC","z".repeat(64)))).andExpect(status().isBadRequest());
    }
    @Test void heartbeatAndTransferLifecycle()throws Exception{
        String token=registerLogin("peer-transfer@example.test");mvc.perform(post("/api/peers/heartbeat").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"peerUid\":\"online-peer\",\"port\":9001}")).andExpect(status().isOk());
        User peerOwner=users.findByEmail("peer-transfer@example.test").orElseThrow();peers.saveAndFlush(new Peer(peerOwner,"stale-peer","127.0.0.1",9002,java.time.Instant.now().minusSeconds(120)));
        mvc.perform(get("/api/peers/online").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].peerUid").value("online-peer")).andExpect(jsonPath("$[1]").doesNotExist());
        long id=createFile(token,"x.bin","PUBLIC","d".repeat(64));String response=mvc.perform(post("/api/transfers").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"fileId\":"+id+"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();long transferId=json.readTree(response).get("id").asLong();
        mvc.perform(patch("/api/transfers/"+transferId).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.finishedAt").exists());
        mvc.perform(patch("/api/transfers/"+transferId).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FAILED\"}")).andExpect(status().isConflict());
    }
    @Test void pagingCapsSizeAtOneHundred()throws Exception{String token=registerLogin("paging@example.test");mvc.perform(get("/api/files?size=500").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));}
}
