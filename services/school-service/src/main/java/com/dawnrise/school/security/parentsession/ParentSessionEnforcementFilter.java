package com.dawnrise.school.security.parentsession;
import com.dawnrise.school.school.provisioning.IdentityProvisioningProperties;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.*;import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
public class ParentSessionEnforcementFilter extends OncePerRequestFilter {
 private final RestClient client;private final IdentityProvisioningProperties properties;
 public ParentSessionEnforcementFilter(RestClient client,IdentityProvisioningProperties properties){this.client=client;this.properties=properties;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  if(client!=null&&properties!=null&&SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token&&token.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_PARENT"))){
   String sessionId=token.getToken().getClaimAsString("sessionId");String userId=token.getToken().getSubject();
   if(sessionId==null||sessionId.isBlank()||userId==null||userId.isBlank()){reject(response,401,"INVALID_PARENT_SESSION_CLAIMS","Parent session claims are missing or invalid");return;}
   try{client.post().uri(uri->uri.path("/internal/v1/parent-sessions/{id}/activity").queryParam("userId",userId).build(sessionId)).header("X-Internal-Api-Key",properties.getApiKey()).header("X-Service-Name",properties.getServiceName()).retrieve().toBodilessEntity();}
   catch(RestClientResponseException exception){if(exception.getStatusCode().value()==423){reject(response,423,"PARENT_SESSION_LOCKED","Parent session is locked");return;}reject(response,503,"PARENT_SESSION_VALIDATION_UNAVAILABLE","Parent session validation is unavailable");return;}
   catch(RestClientException exception){reject(response,503,"PARENT_SESSION_VALIDATION_UNAVAILABLE","Parent session validation is unavailable");return;}
  }chain.doFilter(request,response);
 }
 private void reject(HttpServletResponse response,int status,String code,String message)throws IOException{SecurityContextHolder.clearContext();response.setStatus(status);response.setContentType("application/json");response.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\"}");}
}
