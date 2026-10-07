package br.com.vistoria.core.gateway;

public interface HttpGateway {
    HttpGatewayResponse execute(HttpGatewayRequest request) throws Exception;
}
