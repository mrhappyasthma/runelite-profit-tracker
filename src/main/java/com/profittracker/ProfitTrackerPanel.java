package com.profittracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Arrays;
import java.util.Comparator;

public class ProfitTrackerPanel extends PluginPanel
{
    private final JPanel listPanel;
    private final Runnable onReset;
    private final Font largeFont = FontManager.getRunescapeBoldFont().deriveFont(16f);
    private ProfitTrackerPanelItem[] items = new ProfitTrackerPanelItem[0];
    private ProfitTrackerPanelItem[] lastChangeItems = new ProfitTrackerPanelItem[0];
    private boolean lastChangeCollapsed;
    private boolean gainedCollapsed;
    private boolean lostCollapsed;

    public ProfitTrackerPanel(Runnable onReset)
    {
        super();
        this.onReset = onReset;

        setBorder(new EmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        BoxLayout boxLayout = new BoxLayout(this, BoxLayout.Y_AXIS);
        setLayout(boxLayout);

        JLabel header = new JLabel("Item Changes");
        header.setForeground(Color.WHITE);
        header.setFont(FontManager.getRunescapeBoldFont());
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(header);

        add(createHeaderActions());
        add(createSpacer());

        listPanel = new JPanel();
        listPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        listPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        add(listPanel);

        refresh();
    }

    public void setItems(ProfitTrackerPanelItem[] items, ProfitTrackerPanelItem[] lastChangeItems)
    {
        this.items = items != null ? items : new ProfitTrackerPanelItem[0];
        this.lastChangeItems = lastChangeItems != null ? lastChangeItems : new ProfitTrackerPanelItem[0];
        refresh();
    }

    public void refresh()
    {
        SwingUtilities.invokeLater(() ->
        {
            listPanel.removeAll();

            if (items.length == 0)
            {
                listPanel.add(createMessageLabel("No tracked item changes yet."));
            }
            else
            {
                ProfitTrackerPanelItem[] gainedItems = Arrays.stream(items)
                        .filter(item -> item.getQuantity() > 0)
                        .sorted(Comparator.comparingLong(ProfitTrackerPanelItem::getProfitChange).reversed())
                        .toArray(ProfitTrackerPanelItem[]::new);

                ProfitTrackerPanelItem[] lostItems = Arrays.stream(items)
                        .filter(item -> item.getQuantity() < 0)
                        .sorted(Comparator.comparingLong(ProfitTrackerPanelItem::getProfitChange))
                        .toArray(ProfitTrackerPanelItem[]::new);

                listPanel.add(createNetTotalPanel(gainedItems, lostItems));
                listPanel.add(createSpacer());
                listPanel.add(createCollapsibleSection("Last Change", lastChangeItems, "No tracked changes yet.",
                        lastChangeCollapsed, collapsed -> lastChangeCollapsed = collapsed));
                listPanel.add(createSpacer());
                listPanel.add(createCollapsibleSection("Gained", gainedItems, "No gained items yet.",
                        gainedCollapsed, collapsed -> gainedCollapsed = collapsed));
                listPanel.add(createSpacer());
                listPanel.add(createCollapsibleSection("Lost", lostItems, "No lost items yet.",
                        lostCollapsed, collapsed -> lostCollapsed = collapsed));
            }

            listPanel.revalidate();
            listPanel.repaint();
            revalidate();
            repaint();
        });
    }

    private JPanel createHeaderActions()
    {
        JPanel headerActions = new JPanel();
        headerActions.setLayout(new BoxLayout(headerActions, BoxLayout.X_AXIS));
        headerActions.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerActions.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(e ->
        {
            if (onReset != null)
            {
                onReset.run();
            }
        });

        headerActions.add(Box.createHorizontalGlue());
        headerActions.add(resetButton);
        return headerActions;
    }

    private JPanel createCollapsibleSection(String title, ProfitTrackerPanelItem[] sectionItems, String emptyMessage,
                                            boolean collapsed, SectionCollapseHandler collapseHandler)
    {
        JPanel sectionPanel = new JPanel();
        sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.Y_AXIS));
        sectionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton toggleButton = createSectionToggle(title, getProfitTotal(sectionItems), contentPanel, collapsed, collapseHandler);
        sectionPanel.add(toggleButton);
        sectionPanel.add(createSpacer());

        rebuildSectionContent(contentPanel, sectionItems, emptyMessage);
        contentPanel.setVisible(!collapsed);
        sectionPanel.add(contentPanel);

        return sectionPanel;
    }

    private JPanel createNetTotalPanel(ProfitTrackerPanelItem[] gainedItems, ProfitTrackerPanelItem[] lostItems)
    {
        long netTotal = getProfitTotal(gainedItems) + getProfitTotal(lostItems);

        JPanel totalPanel = new JPanel();
        totalPanel.setLayout(new BoxLayout(totalPanel, BoxLayout.Y_AXIS));
        totalPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        totalPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
        totalPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel("Profit");
        titleLabel.setForeground(Color.LIGHT_GRAY);
        titleLabel.setFont(FontManager.getRunescapeSmallFont());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        totalPanel.add(titleLabel);

        JLabel totalLabel = new JLabel(formatProfit(netTotal));
        totalLabel.setForeground(netTotal >= 0 ? Color.GREEN : Color.RED);
        totalLabel.setFont(largeFont);
        totalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        totalPanel.add(totalLabel);

        return totalPanel;
    }

    private JPanel createSectionHeader(String title, long total)
    {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
        headerPanel.setBackground(ColorScheme.BORDER_COLOR);
        headerPanel.setBorder(new EmptyBorder(4, 6, 4, 6));
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sectionLabel = new JLabel(title);
        sectionLabel.setForeground(Color.WHITE);
        sectionLabel.setFont(FontManager.getRunescapeBoldFont());
        headerPanel.add(sectionLabel);
        headerPanel.add(Box.createHorizontalGlue());

        JLabel totalLabel = new JLabel(formatProfit(total));
        totalLabel.setForeground(total >= 0 ? Color.GREEN : Color.RED);
        totalLabel.setFont(largeFont);
        headerPanel.add(totalLabel);

        return headerPanel;
    }

    private JButton createSectionToggle(String title, long total, JPanel contentPanel, boolean collapsed,
                                        SectionCollapseHandler collapseHandler)
    {
        JButton toggleButton = new JButton();
        toggleButton.setLayout(new BoxLayout(toggleButton, BoxLayout.X_AXIS));
        toggleButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggleButton.setBackground(ColorScheme.BORDER_COLOR);
        toggleButton.setBorderPainted(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setContentAreaFilled(true);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setOpaque(true);
        toggleButton.setBorder(new EmptyBorder(4, 6, 4, 6));

        JLabel sectionLabel = new JLabel();
        sectionLabel.setForeground(Color.WHITE);
        sectionLabel.setFont(FontManager.getRunescapeBoldFont());

        JLabel totalLabel = new JLabel(formatProfit(total));
        totalLabel.setForeground(total >= 0 ? Color.GREEN : Color.RED);
        totalLabel.setFont(largeFont);

        toggleButton.add(sectionLabel);
        toggleButton.add(Box.createHorizontalGlue());
        toggleButton.add(totalLabel);

        final boolean[] sectionCollapsed = {collapsed};
        Runnable updateButtonText = () -> sectionLabel.setText(String.format("%-2s%s", sectionCollapsed[0] ? "+" : "-", title));
        updateButtonText.run();

        toggleButton.addActionListener(e ->
        {
            sectionCollapsed[0] = !sectionCollapsed[0];
            collapseHandler.setCollapsed(sectionCollapsed[0]);
            contentPanel.setVisible(!sectionCollapsed[0]);
            updateButtonText.run();
            revalidate();
            repaint();
        });

        return toggleButton;
    }

    @FunctionalInterface
    private interface SectionCollapseHandler
    {
        void setCollapsed(boolean collapsed);
    }

    private void rebuildSectionContent(JPanel contentPanel, ProfitTrackerPanelItem[] sectionItems, String emptyMessage)
    {
        contentPanel.removeAll();

        if (sectionItems.length == 0)
        {
            contentPanel.add(createMessageLabel(emptyMessage));
            return;
        }

        for (ProfitTrackerPanelItem item : sectionItems)
        {
            try
            {
                contentPanel.add(createItemRow(item));
            }
            catch (Exception ex)
            {
                contentPanel.add(createFallbackRow(item));
            }
        }
    }

    private JPanel createItemRow(ProfitTrackerPanelItem item)
    {
        JPanel itemContainer = new JPanel();
        itemContainer.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        itemContainer.setLayout(new BoxLayout(itemContainer, BoxLayout.X_AXIS));
        itemContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        itemContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel iconLabel = new JLabel(new ImageIcon(item.getImage()));
        iconLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        itemContainer.add(iconLabel);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.setBorder(new EmptyBorder(0, 4, 0, 0));
        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        itemContainer.add(contentPanel);

        JLabel nameLabel = new JLabel(item.getName(), SwingConstants.LEFT);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(nameLabel);

        JLabel valueLabel = new JLabel(formatProfit(item.getProfitChange()), SwingConstants.RIGHT);
        valueLabel.setFont(FontManager.getRunescapeSmallFont());
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(valueLabel);

        return itemContainer;
    }

    private JPanel createFallbackRow(ProfitTrackerPanelItem item)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel idLabel = new JLabel(item.getName());
        idLabel.setForeground(Color.WHITE);
        row.add(idLabel);

        row.add(Box.createHorizontalGlue());

        JLabel valueLabel = new JLabel(formatProfit(item.getProfitChange()), SwingConstants.RIGHT);
        valueLabel.setForeground(item.getProfitChange() >= 0 ? Color.GREEN : Color.RED);
        valueLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(valueLabel);

        return row;
    }

    private static JPanel createSpacer()
    {
        JPanel spacer = new JPanel();
        spacer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        spacer.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        spacer.setAlignmentX(Component.LEFT_ALIGNMENT);
        return spacer;
    }

    private static JLabel createMessageLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(Color.LIGHT_GRAY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return label;
    }

    private static long getProfitTotal(ProfitTrackerPanelItem[] sectionItems)
    {
        return Arrays.stream(sectionItems)
                .mapToLong(ProfitTrackerPanelItem::getProfitChange)
                .sum();
    }

    private static String formatProfit(long profitChange)
    {
        return String.format("%s%,d gp", profitChange >= 0 ? "+" : "", profitChange);
    }
}
