package wtf.oraculus.client.auth;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import wtf.oraculus.client.ReleaseInfo;

public final class OraculusLoginScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 286;

    private final Screen parent;
    private final AuthService auth;
    private TextFieldWidget username;
    private TextFieldWidget password;
    private TextFieldWidget confirmPassword;
    private ButtonWidget submit;
    private ButtonWidget loginTab;
    private ButtonWidget registerTab;
    private boolean registration;
    private boolean remember;
    private String localMessage = "";

    public OraculusLoginScreen(final Screen parent) {
        super(Text.literal("Oraculus 登录"));
        this.parent = parent;
        this.auth = AuthBootstrap.getService();
    }

    @Override
    protected void init() {
        final int x = (width - PANEL_WIDTH) / 2;
        final int y = Math.max(20, (height - PANEL_HEIGHT) / 2);
        final int contentX = x + 30;
        final int fieldWidth = PANEL_WIDTH - 60;

        loginTab = addDrawableChild(ButtonWidget.builder(Text.literal("登录"), button -> setRegistration(false))
                .dimensions(contentX, y + 46, fieldWidth / 2 - 4, 20).build());
        registerTab = addDrawableChild(ButtonWidget.builder(Text.literal("注册 Free"), button -> setRegistration(true))
                .dimensions(contentX + fieldWidth / 2 + 4, y + 46, fieldWidth / 2 - 4, 20).build());

        username = new TextFieldWidget(textRenderer, contentX, y + 88, fieldWidth, 20, Text.literal("用户名"));
        username.setMaxLength(24);
        username.setPlaceholder(Text.literal("用户名"));
        username.setText(auth == null ? "" : auth.savedUsername());
        addDrawableChild(username);

        password = passwordField(contentX, y + 122, fieldWidth, "密码（12-128 位）");
        addDrawableChild(password);
        confirmPassword = passwordField(contentX, y + 156, fieldWidth, "确认密码");
        addDrawableChild(confirmPassword);

        final boolean rememberSupported = auth != null && auth.supportsRememberLogin();
        addDrawableChild(CheckboxWidget.builder(
                        Text.literal(rememberSupported ? "记住本机登录" : "当前系统不支持安全保存会话"),
                        textRenderer)
                .pos(contentX, y + 190)
                .checked(false)
                .callback((checkbox, checked) -> remember = rememberSupported && checked)
                .maxWidth(fieldWidth)
                .build()).active = rememberSupported;

        submit = addDrawableChild(ButtonWidget.builder(Text.literal("登录"), button -> submit())
                .dimensions(contentX, y + 226, fieldWidth, 22).build());
        setRegistration(registration);
        setInitialFocus(username);
    }

    private TextFieldWidget passwordField(final int x, final int y, final int width, final String placeholder) {
        final TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setMaxLength(128);
        field.setPlaceholder(Text.literal(placeholder));
        field.addFormatter((value, firstCharacterIndex) ->
                OrderedText.styledForwardsVisitedString("•".repeat(value.length()), Style.EMPTY));
        return field;
    }

    private void setRegistration(final boolean value) {
        registration = value;
        if (confirmPassword != null) confirmPassword.setVisible(value);
        if (submit != null) submit.setMessage(Text.literal(value ? "创建 Free 账号" : "登录"));
        if (loginTab != null) loginTab.active = value;
        if (registerTab != null) registerTab.active = !value;
        localMessage = value ? "新账号固定为 Free，Beta 只能由管理员开通" : "";
    }

    private void submit() {
        if (auth == null) {
            localMessage = "认证组件尚未初始化";
            return;
        }
        final String name = username.getText().trim();
        final String secret = password.getText();
        if (!name.matches("[A-Za-z0-9_]{3,24}")) {
            localMessage = "用户名须为 3-24 位字母、数字或下划线";
            return;
        }
        if (secret.length() < 12 || secret.length() > 128) {
            localMessage = "密码长度须为 12-128 个字符";
            return;
        }
        if (registration && !secret.equals(confirmPassword.getText())) {
            localMessage = "两次输入的密码不一致";
            return;
        }
        localMessage = "";
        if (registration) auth.register(name, secret, remember);
        else auth.login(name, secret, remember);
        password.setText("");
        confirmPassword.setText("");
    }

    @Override
    public void tick() {
        if (auth == null || submit == null) return;
        final AuthState state = auth.snapshot().state();
        submit.active = state != AuthState.AUTHENTICATING
                && state != AuthState.CHECKING_SAVED_SESSION
                && state != AuthState.RUNTIME_STARTING;
    }

    @Override
    public void render(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
        context.fill(0, 0, width, height, 0xFF090B0F);
        final int x = (width - PANEL_WIDTH) / 2;
        final int y = Math.max(20, (height - PANEL_HEIGHT) / 2);
        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xF0191D24);
        drawBorder(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF353D49);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("ORACULUS").formatted(Formatting.BOLD), width / 2, y + 17, 0xFFF4F7FB);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(ReleaseInfo.getEditionLabel() + " / " + ReleaseInfo.VERSION),
                width / 2, y + 30, 0xFF8E9AA9);

        final AuthSnapshot current = auth == null ? AuthSnapshot.initial() : auth.snapshot();
        final String message = localMessage.isBlank() ? current.message() : localMessage;
        if (!message.isBlank()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(message, PANEL_WIDTH - 32),
                    width / 2, y + 258,
                    current.state() == AuthState.ACCESS_REVOKED ? 0xFFFF7777 : 0xFFE6C36A);
        }
        if (!current.requestId().isBlank()) {
            context.drawTextWithShadow(textRenderer, "请求 ID: " + current.requestId(),
                    x + 8, y + PANEL_HEIGHT - 12, 0xFF697483);
        }
        if (auth != null) {
            context.drawTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(auth.fingerprintStatus(), PANEL_WIDTH - 16),
                    x + 8, y + PANEL_HEIGHT - 24, 0xFF697483);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (auth != null && auth.snapshot().state() == AuthState.READY && client != null)
            client.setScreen(parent);
    }

    public Screen parentScreen() {
        return parent;
    }

    private static void drawBorder(final DrawContext context, final int x, final int y,
                                   final int width, final int height, final int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
