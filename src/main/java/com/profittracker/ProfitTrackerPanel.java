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
    private ProfitTrackerPanelItem[] items = new ProfitTrackerPanelItem[0];

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
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        add(listPanel);

        refresh();
    }

    public void setItems(ProfitTrackerPanelItem[] items)
    {
        this.items = items != null ? items : new ProfitTrackerPanelItem[0];
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
                listPanel.add(createSection("Gained", gainedItems, "No gained items yet."));
                listPanel.add(createSpacer());
                listPanel.add(createSection("Lost", lostItems, "No lost items yet."));
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

    private JPanel createSection(String title, ProfitTrackerPanelItem[] sectionItems, String emptyMessage)
    {
        JPanel sectionPanel = new JPanel();
        sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.Y_AXIS));
        sectionPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        sectionPanel.add(createSectionHeader(title, getProfitTotal(sectionItems)));
        sectionPanel.add(createSpacer());

        if (sectionItems.length == 0)
        {
            sectionPanel.add(createMessageLabel(emptyMessage));
            return sectionPanel;
        }

        for (ProfitTrackerPanelItem item : sectionItems)
        {
            try
            {
                sectionPanel.add(createItemRow(item));
            }
            catch (Exception ex)
            {
                sectionPanel.add(createFallbackRow(item));
            }
        }

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
        totalLabel.setFont(createLargeFont());
        totalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        totalPanel.add(totalLabel);

        return totalPanel;
    }

    private JPanel createSectionHeader(String title, long total)
    {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sectionLabel = new JLabel(title);
        sectionLabel.setForeground(Color.WHITE);
        sectionLabel.setFont(FontManager.getRunescapeBoldFont());
        headerPanel.add(sectionLabel);
        headerPanel.add(Box.createHorizontalGlue());

        JLabel totalLabel = new JLabel(formatProfit(total));
        totalLabel.setForeground(total >= 0 ? Color.GREEN : Color.RED);
        totalLabel.setFont(createLargeFont());
        headerPanel.add(totalLabel);

        return headerPanel;
    }

    private JPanel createItemRow(ProfitTrackerPanelItem item)
    {
        JPanel itemContainer = new JPanel();
        itemContainer.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        itemContainer.setLayout(new BoxLayout(itemContainer, BoxLayout.X_AXIS));
        itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel iconLabel = new JLabel(new ImageIcon(item.getImage()));
        iconLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        itemContainer.add(iconLabel);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
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
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
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
        return label;
    }

    private static Font createLargeFont()
    {
        return FontManager.getRunescapeBoldFont().deriveFont(16f);
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
