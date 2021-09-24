//make ghidra decompiler tokens highlight
//@author asmjmp0
//@category decompiler
//@keybinding
//@menupath
//@toolbar

import docking.widgets.EventTrigger;
import docking.widgets.fieldpanel.support.FieldLocation;
import ghidra.app.decompiler.*;
import ghidra.app.decompiler.component.*;
import ghidra.app.plugin.core.decompile.DecompilePlugin;
import ghidra.app.plugin.core.decompile.DecompilerProvider;
import ghidra.app.plugin.core.decompile.PrimaryDecompilerProvider;
import ghidra.app.script.GhidraScript;
import ghidra.framework.plugintool.Plugin;
import ghidra.framework.plugintool.PluginTool;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class HighLightDecompiler extends GhidraScript {
    Color  TransparentColor = new Color(0xff,0xff,0xff,0);
    static private long nowCode = -1;
    static private ArrayList<ClangNode> staticClangNodes = new ArrayList<>();

    private ArrayList<ClangNode> getClangNodesReflect(ClangTokenGroup clangTokenGroup) throws NoSuchFieldException, IllegalAccessException {
        Field tokgroupField = ClangTokenGroup.class.getDeclaredField("tokgroup");
        tokgroupField.setAccessible(true);
        return (ArrayList<ClangNode>) (tokgroupField.get(clangTokenGroup));
    }
    private void getClangNodeFromTokenGroup(ArrayList<ClangNode> clangNodes) throws NoSuchFieldException, IllegalAccessException {
        for (ClangNode node:clangNodes){
            if (node instanceof ClangTokenGroup){
                getClangNodeFromTokenGroup(getClangNodesReflect((ClangTokenGroup)node));
            }else if (node instanceof ClangToken){
                staticClangNodes.add(node);
            }else {
                println(node.getClass().toString());
            }
        }
    }


    private void updateClangNodes(DecompilerLocation decompilerLocation) throws NoSuchFieldException, IllegalAccessException {
        if (nowCode == -1 || staticClangNodes.isEmpty() || nowCode != decompilerLocation.getDecompile().hashCode()){
            DecompileResults decompileResults = decompilerLocation.getDecompile();
            ClangTokenGroup clangTokenGroup = decompileResults.getCCodeMarkup();
            ArrayList<ClangNode> clangNodes =  getClangNodesReflect(clangTokenGroup);
            //get all tokens
            staticClangNodes.clear();
            getClangNodeFromTokenGroup(clangNodes);
            nowCode = decompilerLocation.getDecompile().hashCode();

        }
    }

    private void clearHighlight(){
        for (ClangNode node:staticClangNodes){node.setHighlight(TransparentColor);}
    }

    private void setOtherHighlight(ClangToken currentToken,Color currentColor) {
        for (ClangNode node:staticClangNodes){
            //clear color
            node.setHighlight(TransparentColor);
            //set highlight
            if (currentToken.toString().equals(node.toString())){
                node.setHighlight(currentColor);
            }
        }
    }

    public void run() throws Exception {
       PluginTool pluginTool = getState().getTool();
        List<Plugin> plugins = pluginTool.getManagedPlugins();
        for (Plugin plugin:plugins){
            if (plugin instanceof DecompilePlugin){
                Field field = plugin.getClass().getDeclaredField("connectedProvider");
                field.setAccessible(true);
                PrimaryDecompilerProvider primaryDecompilerProvider = (PrimaryDecompilerProvider)field.get(plugin);
                Field controllerField = DecompilerProvider.class.getDeclaredField("controller");
                controllerField.setAccessible(true);
                DecompilerController controller = (DecompilerController)controllerField.get(primaryDecompilerProvider);
                controller.getDecompilerPanel().setHighlightController(new ClangHighlightController() {
                    @Override
                    public void fieldLocationChanged(FieldLocation location, docking.widgets.fieldpanel.field.Field field, EventTrigger eventTrigger) {
                        this.clearPrimaryHighlights();
                        if (field instanceof ClangTextField) {
                            ClangToken tok = ((ClangTextField)field).getToken(location);
                            if (tok != null) {
                                if (tok instanceof ClangSyntaxToken) {
                                    clearHighlight();
                                    tok.setHighlight(this.defaultHighlightColor);
                                    this.addPrimaryHighlightToTokensForParenthesis((ClangSyntaxToken)tok, this.defaultParenColor);
                                    this.addHighlightBrace((ClangSyntaxToken)tok, this.defaultParenColor);
                                    return;
                                }
                                try {
                                    updateClangNodes((DecompilerLocation) primaryDecompilerProvider.getLocation());
                                    setOtherHighlight(tok,this.defaultHighlightColor);
                                } catch (NoSuchFieldException e) {
                                    e.printStackTrace();
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }


                            }
                        }
                    }
                });
                break;
            }
        }
    }
}
