import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.*;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;
import java.util.logging.LogManager;


@ExtensionInfo(
        Title = "GFootBall",
        Description = "Known as Non DC Bot (bye Ahmed)",
        Version = "1.0.6",
        Author = "Julianty"
)

// This library was used: https://github.com/kwhat/jnativehook
public class GFootBall extends ExtensionForm implements NativeKeyListener {
    public TextField textBallID;
    public RadioButton radioButtonShoot, radioButtonTrap, radioButtonDribble,
            radioButtonDoubleClick, radioButtonWalk, radioButtonRun;
    public CheckBox checkBall, checkDisableDouble, checkClickThrough, checkGuideTile, checkHideBubble, checkGuideTrap;
    public Text textName, textIndex, textYourCoords, textBallCoords;

    public String YourName;
    public int CurrentX, CurrentY, BallX, BallY;
    public int ClickX, ClickY;
    public int YourIndex = -1;
    public boolean flagBallTrap = false, flagBallDribble = false, guideTrap = false;

    /*
    [StartTyping]
Outgoing[2395] -> [0][0][0][2][9][91]
{out:StartTyping}
--------------------
[UserTyping]
Incoming[2969] -> [0][0][0][10][11][153][0][0][0][0][0][0][0][1]
{in:UserTyping}{i:0}{i:1}
	  userIndex  state: empieza a escribir
--------------------
[CancelTyping]
Outgoing[3575] -> [0][0][0][2][13]÷
{out:CancelTyping}
--------------------
[UserTyping]
Incoming[2969] -> [0][0][0][10][11][153][0][0][0][0][0][0][0][0]
{in:UserTyping}{i:0}{i:0}
	  userIndex  state: termina de escribir
--------------------
     */

    @Override
    protected void onShow() {
        sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER)); // When its sent, gets UserObject packet
        sendToServer(new HPacket("AvatarExpression", HMessage.Direction.TOSERVER, 0));  // With this it's not necessary to restart the room

        LogManager.getLogManager().reset(); // https://stackoverflow.com/questions/30560212/how-to-remove-the-logging-data-from-jnativehook-library
        try {
            GlobalScreen.registerNativeHook();
            System.out.println("Hook enabled");
        }
        catch (NativeHookException ex) {
            System.err.println("There was a problem registering the native hook.");
            System.err.println(ex.getMessage());

            System.exit(1);
        }
        GlobalScreen.addNativeKeyListener(this);
    }

    @Override
    protected void onHide() {
        YourIndex = -1;
        sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT, "1" /* "1" = id */, false, 8636337, 0));
        sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT, "2", false, 8636337, 0));

        Platform.runLater(()->{
            checkGuideTile.setSelected(false);
            checkGuideTrap.setSelected(false);
        });

        try {
            GlobalScreen.unregisterNativeHook();
            System.out.println("Hook disabled");
        } catch (NativeHookException nativeHookException) {
            nativeHookException.printStackTrace();
        }
    }

    @Override
    protected void initExtension() {
        // Response of packet InfoRetrieve
        intercept(HMessage.Direction.TOCLIENT, "UserObject", hMessage -> {
            // Gets ID and Name in order.
            int YourID = hMessage.getPacket().readInteger();    YourName = hMessage.getPacket().readString();
            textName.setText("Your Name: " + YourName);
        });

        // Response of packet AvatarExpression (gets userIndex)
        intercept(HMessage.Direction.TOCLIENT, "Expression", hMessage -> {
            // First integer is index in room, second is animation id, i think
            if(primaryStage.isShowing() && YourIndex == -1){ // this could avoid any bug
                YourIndex = hMessage.getPacket().readInteger();
                textIndex.setText("Your Index: " + YourIndex);  // GUI updated!
            }
        });

        // Intercepts when you start typing
        intercept(HMessage.Direction.TOSERVER, "StartTyping", hMessage -> {
            if(primaryStage.isShowing() && checkHideBubble.isSelected()){   // If the window is open and the control is checked, it will do that
                hMessage.setBlocked(true);
            }
        });

        // Intercepts this packet when you enter or any user arrive to the room
        intercept(HMessage.Direction.TOCLIENT, "Users", hMessage -> {
            try {
                HPacket hPacket = hMessage.getPacket();
                HEntity[] roomUsersList = HEntity.parse(hPacket);
                for (HEntity hEntity: roomUsersList){
                    if(YourName.equals(hEntity.getName())){
                        YourIndex = hEntity.getIndex();
                    }
                }
            } catch (NullPointerException ignored) { }
            if(checkClickThrough.isSelected()){
                sendToClient(new HPacket("YouArePlayingGame", HMessage.Direction.TOCLIENT, true));
            }
        });

        // Intercepts when users walk in the room
        intercept(HMessage.Direction.TOCLIENT, "UserUpdate", hMessage -> {
            HPacket hPacket = hMessage.getPacket();
            // The HEntityUpdate class allows obtain the index of the user who is walking and other things
            for (HEntityUpdate hEntityUpdate: HEntityUpdate.parse(hPacket)){
                try {
                    int CurrentIndex = hEntityUpdate.getIndex();
                    if(CurrentIndex == YourIndex){
                        textIndex.setText("Your Index: " + CurrentIndex);

                        int JokerX = hEntityUpdate.getTile().getX(); int JokerY = hEntityUpdate.getTile().getY(); // Necesario para el modo de trap
                        if(checkGuideTrap.isSelected()){
                            if(JokerX == BallX && JokerY == BallY){
                                sendToClient(new HPacket("{in:Chat}{i:-1}{s:\"You are on the ball\"}{i:0}{i:30}{i:0}{i:0}"));
                                guideTrap = true;
                            }
                            else{
                                guideTrap = false; // Resuelve el problema de que se quede en el trap
                            }
                        }

                        if(radioButtonRun.isSelected()){
                            CurrentX = hEntityUpdate.getMovingTo().getX();  CurrentY = hEntityUpdate.getMovingTo().getY();
                        }
                        if(radioButtonWalk.isSelected()){
                            CurrentX = hEntityUpdate.getTile().getX();  CurrentY = hEntityUpdate.getTile().getY();
                        }
                        textYourCoords.setText("Your Coords: (" + CurrentX + ", " + CurrentY + ")");

                        if(flagBallTrap){
                            if(BallX - 1 == CurrentX && BallY - 1 == CurrentY){
                                kickBall(1, 1);
                                flagBallTrap = false;
                            }
                            if(BallX + 1 == CurrentX && BallY - 1 == CurrentY){
                                kickBall(-1, 1);
                                flagBallTrap =  false;
                            }
                            if(BallX - 1 == CurrentX && BallY + 1 == CurrentY){
                                kickBall(1, -1);
                                flagBallTrap = false;
                            }
                            if(BallX + 1 == CurrentX && BallY + 1 == CurrentY){
                                kickBall(-1 , -1);
                                flagBallTrap = false;
                            }
                        }
                        if(flagBallDribble){
                            if(BallX - 1 == CurrentX && BallY - 1 == CurrentY){
                                kickBall(2, 2);
                                flagBallDribble = false;
                            }
                            if(BallX + 1 == CurrentX && BallY - 1 == CurrentY){
                                kickBall(-2, 2);
                                flagBallDribble =  false;
                            }
                            if(BallX - 1 == CurrentX && BallY + 1 == CurrentY){
                                kickBall(2, -2);
                                flagBallDribble = false;
                            }
                            if(BallX + 1 == CurrentX && BallY + 1 == CurrentY){
                                kickBall(-2, -2);
                                flagBallDribble = false;
                            }
                        }
                    }
                }
                catch (NullPointerException nullPointerException) {/*getMovingTo() throws a NullPointerException error*/}
            }
        });

        intercept(HMessage.Direction.TOSERVER, "MoveAvatar", hMessage -> {
            if(guideTrap){
                ClickX = hMessage.getPacket().readInteger();    ClickY = hMessage.getPacket().readInteger();
                Suggest(ClickX, ClickY);
                hMessage.setBlocked(true);
            }
        });

        // Intercepts when the users kick the soccer ball
        intercept(HMessage.Direction.TOCLIENT, "ObjectUpdate", hMessage -> {
            try {
                int FurniID = hMessage.getPacket().readInteger();
                if(FurniID == Integer.parseInt(textBallID.getText())){
                    int UniqueID = hMessage.getPacket().readInteger();
                    BallX = hMessage.getPacket().readInteger();
                    BallY = hMessage.getPacket().readInteger();
                    textBallCoords.setText("Ball Coords: (" + BallX + ", " + BallY + ")");
                }
            }
            catch (Exception ignored){ }
        });

        /* When you move ball with admin rights, useful in holos i think, so ignore this
        intercept(HMessage.Direction.TOCLIENT, 3776, hMessage -> {
            try {
                int FurniID = hMessage.getPacket().readInteger();
                int UniqueID = hMessage.getPacket().readInteger();
                int X = hMessage.getPacket().readInteger();
                int Y = hMessage.getPacket().readInteger();
                if(FurniID == Integer.parseInt(textBallID.getText())){
                    BallX = X; BallY = Y;
                    textBallCoords.setText("Ball Coords: (" + BallX + ", " + BallY + ")");
                }
            }
            catch (Exception ignored){ }
        }); */

        // Intercepts when you give double click on a furniture
        intercept(HMessage.Direction.TOSERVER, "UseFurniture", hMessage -> {
            if(checkDisableDouble.isSelected()){
                hMessage.setBlocked(true);
            }
            else if(checkBall.isSelected() && !checkDisableDouble.isSelected()){
                int BallID = hMessage.getPacket().readInteger();
                textBallID.setText(String.valueOf(BallID));
                checkBall.setSelected(false);
            }
        });
    }

    public void kickBall(int PlusX, int PlusY){
        // Moves the tile in the client-side
        sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                1, 8237, BallX + PlusX, BallY + PlusY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        // Moves the user in the server-side
        sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX + PlusX, BallY + PlusY));
        flagBallTrap = false;
    }

    public void Suggest(int ClickX, int ClickY){
        if(ClickX == BallX - 1 && ClickY == BallY - 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, BallX + 6, BallY + 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == BallX + 1 && ClickY == BallY + 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, BallX - 6, BallY - 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == BallX - 1 && ClickY == BallY + 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, BallX + 6, BallY - 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == BallX + 1 && ClickY == BallY - 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, BallX - 6, BallY + 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }

        if(ClickX == BallX - 1 && ClickY == BallY){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, BallX + 6, BallY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == BallX + 1 && ClickY == BallY){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, BallX - 6, BallY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == BallX && ClickY == BallY + 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, BallX, BallY - 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == BallX && ClickY == BallY - 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, BallX, BallY + 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        sendToClient(new HPacket("{in:Chat}{i:-1}{s:\"Remember to press the ESCAPE key to kick\"}{i:0}{i:30}{i:0}{i:0}"));
    }

    public void handleClickThrough() {
        if(checkClickThrough.isSelected()){
            sendToClient(new HPacket("YouArePlayingGame", HMessage.Direction.TOCLIENT, true));  // Enable Click Through
        }
        else{
            sendToClient(new HPacket("YouArePlayingGame", HMessage.Direction.TOCLIENT, false)); // Disable Click Through
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) { }

    // I dont want to type in the chat when i press a key but unfortunately this in java cannot be solved :(, i think
    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
        NativeKeyEvent.getKeyText(nativeKeyEvent.getKeyCode());
        if(nativeKeyEvent.getKeyCode() == NativeKeyEvent.VC_ESCAPE){
            guideTrap = false;
            sendToServer(new HPacket("{out:MoveAvatar}{i:"+ClickX+"}{i:"+ClickY+"}"));
        }

        flagBallTrap = false;   flagBallDribble = false;    // restart booleans
        if(nativeKeyEvent.getKeyCode() == NativeKeyEvent.VC_1){ // Key 1
            radioButtonShoot.setSelected(true);
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    1, 8237, BallX, BallY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX, BallY));
        }
        if(nativeKeyEvent.getKeyCode() == NativeKeyEvent.VC_2){ // Key 2
            radioButtonTrap.setSelected(true);
            // En habbo futbol "Trap" significa pisar, el usuario caminara una casilla al frente del balon

            // Example -> Ball coords (8, 5) ; User up (8, 4)
            if (BallX == CurrentX && BallY > CurrentY)
            {
                kickBall(0, 1);
            }
            // Example -> Ball coords (8, 5) ; User down (8, 6)
            if (BallX == CurrentX && BallY < CurrentY)
            {
                kickBall(0, -1);
            }
            // Example -> Ball coords (8, 5) ; User left (7, 5)
            if (BallX > CurrentX && BallY == CurrentY)
            {
                kickBall(1, 0);
            }
            // Example -> Ball coords (8, 5) ; User right (9, 5)
            if (BallX < CurrentX && BallY == CurrentY)
            {
                kickBall(-1, 0);
            }

            // Example -> Ball coords (8, 5) ; User corner top left (7, 4)
            if (BallX > CurrentX && BallY > CurrentY)
            {
                if(BallX - 1 == CurrentX && BallY - 1 == CurrentY){
                    kickBall(1, 1);
                }
                else {
                    sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                            1, 8237, BallX - 1, BallY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                    sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX - 1, BallY - 1));
                    flagBallTrap =  true;
                }
            }
            // Example -> Ball coords (8, 5) ; User corner top right (9, 4)
            if (BallX < CurrentX && BallY > CurrentY)
            {
                if(BallX + 1 == CurrentX && BallY - 1 == CurrentY){
                    kickBall(-1, 1);
                }
                else {
                    sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                            1, 8237, BallX + 1, BallY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                    sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX + 1, BallY - 1));
                    flagBallTrap =  true;
                }
            }
            // Example -> Ball coords (8, 5) ; User corner lower left (7, 6)
            if (BallX > CurrentX && BallY < CurrentY)
            {
                if(BallX - 1 == CurrentX && BallY + 1 == CurrentY){
                    kickBall(1, -1);
                }
                else {
                    sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                            1, 8237, BallX - 1, BallY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                    sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX - 1, BallY + 1));
                    flagBallTrap =  true;
                }
            }
            // Example -> Ball coords (8, 5) ; User corner lower right (9, 6)
            if (BallX < CurrentX && BallY < CurrentY)
            {
                if(BallX + 1 == CurrentX && BallY + 1 == CurrentY){
                    kickBall(-1 , -1);
                }
                else {
                    sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                            1, 8237, BallX + 1, BallY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                    sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX + 1, BallY + 1));
                    flagBallTrap =  true;
                }
            }
        }
        if(nativeKeyEvent.getKeyCode() == NativeKeyEvent.VC_3){   // Key 3
            radioButtonDribble.setSelected(true);
            // En habbo futbol "Dribble" significa caminar, el usuario caminara dos casillas al frente del balon

            // Example -> Ball coords (8, 5) ; User up (8, 4)
            if (BallX == CurrentX && BallY > CurrentY)
            {
                kickBall(0 ,2);
            }
            // Example -> Ball coords (8, 5) ; User down (8, 6)
            if (BallX == CurrentX && BallY < CurrentY)
            {
                kickBall(0, -2);
            }
            // Example -> Ball coords (8, 5) ; User left (7, 5)
            if (BallX > CurrentX && BallY == CurrentY)
            {
                kickBall(2, 0);
            }
            // Example -> Ball coords (8, 5) ; User right (9, 5)
            if (BallX < CurrentX && BallY == CurrentY)
            {
                kickBall(-2 , 0);
            }

            // Example -> Ball coords (8, 5) ; User corner top left (7, 4)
            if (BallX > CurrentX && BallY > CurrentY)
            {
                if(BallX - 1 == CurrentX && BallY - 1 == CurrentY){ // BallX - 2 == CurrentX && BallY - 2 == CurrentY
                    kickBall(2, 2);
                }
                else {
                    sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                            1, 8237, BallX - 1, BallY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                    sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX - 1, BallY - 1));
                    flagBallDribble =  true;
                }
            }
            // Example -> Ball coords (8, 5) ; User corner top right (9, 4)
            if (BallX < CurrentX && BallY > CurrentY)
            {
                if(BallX + 1 == CurrentX && BallY - 1 == CurrentY){
                    kickBall(-2 , 2);
                }
                else {
                    sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                            1, 8237, BallX + 1, BallY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                    sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX + 1, BallY - 1));
                    flagBallDribble =  true;
                }
            }
            // Example -> Ball coords (8, 5) ; User corner lower left (7, 6)
            if (BallX > CurrentX && BallY < CurrentY)
            {
                if(BallX - 1 == CurrentX && BallY + 1 == CurrentY){
                    kickBall(2, -2);
                }
                else {
                    sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                            1, 8237, BallX - 1, BallY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                    sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX - 1, BallY + 1));
                    flagBallDribble =  true;
                }
            }
            // Example -> Ball coords (8, 5) ; User corner lower right (9, 6)
            if (BallX < CurrentX && BallY < CurrentY)
            {
                if(BallX + 1 == CurrentX && BallY + 1 == CurrentY){
                    kickBall(-2 , -2);
                }
                else {
                    sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                            1, 8237, BallX + 1, BallY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                    sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX + 1, BallY + 1));
                    flagBallDribble =  true;
                }
            }
        }
        if(nativeKeyEvent.getKeyCode() == NativeKeyEvent.VC_4){   // Key 4
            radioButtonDoubleClick.setSelected(true);
            sendToServer(new HPacket("UseFurniture", HMessage.Direction.TOSERVER,
                    Integer.parseInt(textBallID.getText()), 0));
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT, 1, 8237, BallX, BallY,
                    0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) { }

    public void handleGuideTile() {
        if(checkGuideTile.isSelected()){
            sendToClient(new HPacket("ObjectAdd", HMessage.Direction.TOCLIENT, 1, 5399, 0
                    , 0, 0, "0.0" /*"3.5"*/, "0.2", 0, 0, "1", -1, 1, 2, YourName));
        }
        else {
            sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT, "1", false, 8636337, 0));
        }
    }

    public void handleGuideTrap(){
        if(checkGuideTrap.isSelected()){
            sendToClient(new HPacket("ObjectAdd", HMessage.Direction.TOCLIENT, 2, 5399, 0
                    , 0, 0, "0.0", "0.2", 0, 0, "1", -1, 1, 2, YourName));
        }
        else {
            sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT, "2", false, 8636337, 0));
        }
    }
}

/* Ventana de confirmacion de dialogo puede ser util

Platform.runLater(() -> {
                Alert alert = ConfirmationDialog.createAlertWithOptOut(Alert.AlertType.WARNING, connectExtensionKey
                        ,"Confirmation Dialog", null,
                        "Extension \""+extension.getTitle()+"\" tries to connect but isn't known to G-Earth, accept this connection?", "Remember my choice",
                        ButtonType.YES, ButtonType.NO
                );

                if (!(alert.showAndWait().filter(t -> t == ButtonType.YES).isPresent())) {
                    allowConnection[0] = false;
                }
                done[0] = true;
                if (!ConfirmationDialog.showDialog(connectExtensionKey)) {
                    rememberOption = allowConnection[0];
                }
            });
 */
