package mindustry.web;

import arc.input.*;

/** Maps browser KeyboardEvent.code values to Arc key codes. */
public final class BrowserKeymap{
    private BrowserKeymap(){}

    public static KeyCode fromCode(String code){
        if(code == null) return KeyCode.unknown;

        if(code.length() == 4 && code.startsWith("Key")){
            return switch(code.charAt(3)){
                case 'A' -> KeyCode.a; case 'B' -> KeyCode.b; case 'C' -> KeyCode.c; case 'D' -> KeyCode.d;
                case 'E' -> KeyCode.e; case 'F' -> KeyCode.f; case 'G' -> KeyCode.g; case 'H' -> KeyCode.h;
                case 'I' -> KeyCode.i; case 'J' -> KeyCode.j; case 'K' -> KeyCode.k; case 'L' -> KeyCode.l;
                case 'M' -> KeyCode.m; case 'N' -> KeyCode.n; case 'O' -> KeyCode.o; case 'P' -> KeyCode.p;
                case 'Q' -> KeyCode.q; case 'R' -> KeyCode.r; case 'S' -> KeyCode.s; case 'T' -> KeyCode.t;
                case 'U' -> KeyCode.u; case 'V' -> KeyCode.v; case 'W' -> KeyCode.w; case 'X' -> KeyCode.x;
                case 'Y' -> KeyCode.y; case 'Z' -> KeyCode.z;
                default -> KeyCode.unknown;
            };
        }

        if(code.length() == 6 && code.startsWith("Digit")){
            return switch(code.charAt(5)){
                case '0' -> KeyCode.num0; case '1' -> KeyCode.num1; case '2' -> KeyCode.num2; case '3' -> KeyCode.num3;
                case '4' -> KeyCode.num4; case '5' -> KeyCode.num5; case '6' -> KeyCode.num6; case '7' -> KeyCode.num7;
                case '8' -> KeyCode.num8; case '9' -> KeyCode.num9;
                default -> KeyCode.unknown;
            };
        }

        return switch(code){
            case "ArrowUp" -> KeyCode.up;
            case "ArrowDown" -> KeyCode.down;
            case "ArrowLeft" -> KeyCode.left;
            case "ArrowRight" -> KeyCode.right;
            case "ShiftLeft" -> KeyCode.shiftLeft;
            case "ShiftRight" -> KeyCode.shiftRight;
            case "ControlLeft" -> KeyCode.controlLeft;
            case "ControlRight" -> KeyCode.controlRight;
            case "AltLeft" -> KeyCode.altLeft;
            case "AltRight" -> KeyCode.altRight;
            case "MetaLeft", "MetaRight" -> KeyCode.sym;
            case "Escape" -> KeyCode.escape;
            case "Enter", "NumpadEnter" -> KeyCode.enter;
            case "Space" -> KeyCode.space;
            case "Tab" -> KeyCode.tab;
            case "Backspace" -> KeyCode.backspace;
            case "Delete" -> KeyCode.forwardDel;
            case "Insert" -> KeyCode.insert;
            case "Home" -> KeyCode.home;
            case "End" -> KeyCode.end;
            case "PageUp" -> KeyCode.pageUp;
            case "PageDown" -> KeyCode.pageDown;
            case "Backquote" -> KeyCode.backtick;
            case "Minus" -> KeyCode.minus;
            case "Equal" -> KeyCode.equals;
            case "BracketLeft" -> KeyCode.leftBracket;
            case "BracketRight" -> KeyCode.rightBracket;
            case "Backslash" -> KeyCode.backslash;
            case "Semicolon" -> KeyCode.semicolon;
            case "Quote" -> KeyCode.apostrophe;
            case "Comma" -> KeyCode.comma;
            case "Period" -> KeyCode.period;
            case "Slash" -> KeyCode.slash;
            case "CapsLock" -> KeyCode.capsLock;
            case "Pause" -> KeyCode.pause;
            case "PrintScreen" -> KeyCode.printScreen;
            case "ScrollLock" -> KeyCode.scrollLock;
            case "F1" -> KeyCode.f1; case "F2" -> KeyCode.f2; case "F3" -> KeyCode.f3; case "F4" -> KeyCode.f4;
            case "F5" -> KeyCode.f5; case "F6" -> KeyCode.f6; case "F7" -> KeyCode.f7; case "F8" -> KeyCode.f8;
            case "F9" -> KeyCode.f9; case "F10" -> KeyCode.f10; case "F11" -> KeyCode.f11; case "F12" -> KeyCode.f12;
            case "Numpad0" -> KeyCode.numpad0; case "Numpad1" -> KeyCode.numpad1; case "Numpad2" -> KeyCode.numpad2;
            case "Numpad3" -> KeyCode.numpad3; case "Numpad4" -> KeyCode.numpad4; case "Numpad5" -> KeyCode.numpad5;
            case "Numpad6" -> KeyCode.numpad6; case "Numpad7" -> KeyCode.numpad7; case "Numpad8" -> KeyCode.numpad8;
            case "Numpad9" -> KeyCode.numpad9;
            default -> KeyCode.unknown;
        };
    }

    public static KeyCode mouseButton(int button){
        return switch(button){
            case 0 -> KeyCode.mouseLeft;
            case 1 -> KeyCode.mouseMiddle;
            case 2 -> KeyCode.mouseRight;
            case 3 -> KeyCode.mouseBack;
            case 4 -> KeyCode.mouseForward;
            default -> KeyCode.mouseLeft;
        };
    }
}
