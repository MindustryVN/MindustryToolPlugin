package mindustrytool.skeleton;

import java.rmi.RemoteException;

import mindustrytool.messages.request.SetPlayerMessageRequest;
import mindustrytool.messages.request.StartServerMessageRequest;
import mindustrytool.messages.response.StatsMessageResponse;

public interface ApiGateway extends java.rmi.Remote {
    public boolean serverLoaded(String message) throws RemoteException;

    public void discordMessage(String message) throws RemoteException;

    public void startServer(StartServerMessageRequest request) throws RemoteException;

    public void setPlayer(SetPlayerMessageRequest request) throws RemoteException;

    public StatsMessageResponse stats() throws RemoteException;

    public StatsMessageResponse detailStats() throws RemoteException;
}
