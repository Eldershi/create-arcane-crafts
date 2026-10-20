// Send only bounded comparison-scene setblock commands to the owned prototype.
// The user's input source is restored after typing the bounded commands.
import Carbon
import Foundation

let root = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
let run = root.appendingPathComponent("run")
let record = try JSONSerialization.jsonObject(with: Data(contentsOf: run.appendingPathComponent("client-pid.json"))) as! [String: Any]
guard let pid = record["pid"] as? Int,
      record["instance"] as? String == root.appendingPathComponent("instance").path else {
    fatalError("The recorded client does not belong to this prototype")
}
let commands = try JSONSerialization.jsonObject(with: Data(contentsOf: run.appendingPathComponent("comparison-commands.json"))) as! [String]
guard !commands.isEmpty && commands.count <= 20 else { fatalError("Invalid comparison command count") }
for command in commands {
    let pieces = command.split(separator: " ", maxSplits: 4)
    guard pieces.count == 5, pieces[0] == "/setblock",
          let x = Int(pieces[1]), let y = Int(pieces[2]), let z = Int(pieces[3]),
          (-12...12).contains(x), (1...3).contains(y), (4...6).contains(z),
          (["create:blaze_burner", "create:basin", "minecraft:oak_sign"].contains(where: { pieces[4].hasPrefix($0) })
            || (pieces[4] == "minecraft:air" && y == 2 && z == 4 && [0, 2, 4].contains(x))) else {
        fatalError("Only comparison blocks in the prototype display area are allowed")
    }
}

// Minecraft command hotkeys need an ASCII input source; restore the user's
// source afterwards so the Chinese IME cannot turn a command into ordinary chat.
let inputSource = TISCopyCurrentKeyboardInputSource().takeRetainedValue()
let layouts = TISCreateInputSourceList([kTISPropertyInputSourceID as String: "com.apple.keylayout.ABC"] as CFDictionary, false).takeRetainedValue()
guard CFArrayGetCount(layouts) > 0 else { fatalError("ABC keyboard layout is unavailable") }
let asciiSource = unsafeBitCast(CFArrayGetValueAtIndex(layouts, 0), to: TISInputSource.self)
guard TISSelectInputSource(asciiSource) == noErr else { fatalError("Could not select the command keyboard layout") }
defer { TISSelectInputSource(inputSource) }

for (index, command) in commands.enumerated() {
    let script = """
    on run argv
    tell application "System Events"
      set gameProcess to first application process whose unix id is \(pid)
      set frontmost of gameProcess to true
      tell gameProcess
        key code 44
        delay 0.2
        repeat with commandCharacter in characters of (item 1 of argv)
          keystroke (commandCharacter as text)
          delay 0.015
        end repeat
        delay 0.3
        key code 36
        delay 0.25
      end tell
    end tell
    end run
    """
    let process = Process()
    process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
    process.arguments = ["-e", script, String(command.dropFirst())]
    try process.run()
    process.waitUntilExit()
    guard process.terminationStatus == 0 else { fatalError("Could not address the prototype game window") }
    print("Submitted comparison block \(index + 1)/\(commands.count)")
}
