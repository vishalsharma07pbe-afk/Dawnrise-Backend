package com.dawnrise.identity.auth.parentsession.security;
import com.dawnrise.identity.auth.parentsession.exception.RecentParentAuthenticationRequiredException;
import com.dawnrise.identity.auth.parentsession.exception.ParentSessionLockedException;
import com.dawnrise.identity.auth.parentsession.service.ParentSessionService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;
public class ParentSessionFilter extends OncePerRequestFilter {
 private final ParentSessionService sessions;
 public ParentSessionFilter(ParentSessionService sessions){this.sessions=sessions;}
 @Override protected boolean shouldNotFilter(HttpServletRequest request){String path=request.getRequestURI();return path.startsWith("/internal/")||path.equals("/api/v1/auth/parent-session/unlock");}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  if(sessions!=null&&SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token&&token.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_PARENT"))){
   try{UUID sessionId=sessionId(token);Long userId=userId(token);sessions.validateAndTouch(sessionId,userId);if(isSensitive(request)) sessions.requireRecentAuthentication(sessionId,userId);}
   catch(RecentParentAuthenticationRequiredException exception){response.setStatus(403);response.setContentType("application/json");response.getWriter().write("{\"validationErrors\":{\"code\":\"RECENT_PARENT_AUTHENTICATION_REQUIRED\"},\"message\":\"Recent parent authentication is required\"}");return;}
   catch(ParentSessionLockedException exception){SecurityContextHolder.clearContext();response.setStatus(423);response.setContentType("application/json");response.getWriter().write("{\"code\":\"PARENT_SESSION_LOCKED\",\"message\":\"Parent session is locked\"}");return;}
   catch(IllegalArgumentException exception){SecurityContextHolder.clearContext();response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"code\":\"INVALID_PARENT_SESSION_CLAIMS\",\"message\":\"Parent session claims are missing or invalid\"}");return;}
  }
  chain.doFilter(request,response);
 }
 private UUID sessionId(JwtAuthenticationToken token){String value=token.getToken().getClaimAsString("sessionId");if(value==null||value.isBlank())throw new IllegalArgumentException("Missing sessionId");return UUID.fromString(value);}
 private Long userId(JwtAuthenticationToken token){String value=token.getToken().getSubject();if(value==null||value.isBlank())throw new IllegalArgumentException("Missing subject");return Long.valueOf(value);}
 private boolean isSensitive(HttpServletRequest request){String path=request.getRequestURI();return (request.getMethod().equals("POST")&&path.equals("/api/v1/auth/password/change"))||(request.getMethod().equals("PUT")&&path.matches("/api/v1/organizations/[^/]+/users/[^/]+/profile"))||(request.getMethod().equals("POST")&&path.matches("/api/v1/organizations/[^/]+/profile-change-requests/users/[^/]+"));}
}
