import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SupabaseService } from '../../services/supabase.service';
import { CrashLog } from '../../core/models/driver.model';
import { CrashDetailDialogComponent } from './crash-detail-dialog.component';

@Component({
  selector: 'app-crash-logs',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatInputModule,
    MatChipsModule,
    MatDialogModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="crashes-container">
      <!-- Encabezado -->
      <div class="header">
        <div class="title-group">
          <div class="icon-wrap">
            <mat-icon>bug_report</mat-icon>
          </div>
          <div>
            <h2>Reportes de Errores y Fallos (Crashes)</h2>
            <p class="subtitle">
              Telemetría automática enviada por la aplicación móvil tras detectar excepciones no capturadas
            </p>
          </div>
        </div>

        <div class="actions-group">
          <button mat-flat-button class="refresh-btn" (click)="loadCrashLogs()">
            <mat-icon>refresh</mat-icon>
            Actualizar
          </button>
        </div>
      </div>

      <!-- Tarjetas Resumen / KPIs -->
      <div class="kpi-grid">
        <div class="kpi-card total">
          <div class="kpi-icon">
            <mat-icon>analytics</mat-icon>
          </div>
          <div class="kpi-info">
            <span class="kpi-label">Total Reportes</span>
            <span class="kpi-value">{{ totalCount() }}</span>
          </div>
        </div>

        <div class="kpi-card pending">
          <div class="kpi-icon">
            <mat-icon>warning</mat-icon>
          </div>
          <div class="kpi-info">
            <span class="kpi-label">Pendientes de Solución</span>
            <span class="kpi-value">{{ pendingCount() }}</span>
          </div>
        </div>

        <div class="kpi-card resolved">
          <div class="kpi-icon">
            <mat-icon>check_circle</mat-icon>
          </div>
          <div class="kpi-info">
            <span class="kpi-label">Resueltos</span>
            <span class="kpi-value">{{ resolvedCount() }}</span>
          </div>
        </div>
      </div>

      <!-- Filtros y Búsqueda -->
      <div class="filter-row">
        <mat-form-field appearance="outline" class="search-field">
          <mat-icon matPrefix>search</mat-icon>
          <input
            matInput
            (keyup)="applyFilter($event)"
            placeholder="Buscar por dispositivo, versión Android, tipo de error o mensaje..."
          />
        </mat-form-field>

        <div class="status-filters">
          <button
            mat-stroked-button
            class="filter-chip"
            [class.active]="currentFilter() === 'all'"
            (click)="setFilter('all')"
          >
            Todos ({{ totalCount() }})
          </button>
          <button
            mat-stroked-button
            class="filter-chip pending-chip"
            [class.active]="currentFilter() === 'pending'"
            (click)="setFilter('pending')"
          >
            Solo Pendientes ({{ pendingCount() }})
          </button>
          <button
            mat-stroked-button
            class="filter-chip resolved-chip"
            [class.active]="currentFilter() === 'resolved'"
            (click)="setFilter('resolved')"
          >
            Solo Resueltos ({{ resolvedCount() }})
          </button>
        </div>
      </div>

      <!-- Tabla de Incidentes -->
      <mat-card class="table-card">
        @if (isLoading()) {
          <div class="loading-shade">
            <mat-spinner diameter="40"></mat-spinner>
            <p>Cargando reportes de fallos...</p>
          </div>
        }

        <div class="table-container">
          <table mat-table [dataSource]="dataSource" matSort class="dark-table">

            <!-- Estado Column -->
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Estado </th>
              <td mat-cell *matCellDef="let c">
                <span class="status-pill" [class.resolved]="c.resolved" [class.pending]="!c.resolved">
                  <mat-icon class="pill-icon">{{ c.resolved ? 'check_circle' : 'warning' }}</mat-icon>
                  {{ c.resolved ? 'Resuelto' : 'Pendiente' }}
                </span>
              </td>
            </ng-container>

            <!-- Fecha Column -->
            <ng-container matColumnDef="created_at">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Fecha / Hora </th>
              <td mat-cell *matCellDef="let c">
                <div class="date-cell">
                  <span class="primary-date">{{ c.created_at | date:'dd/MM/yyyy' }}</span>
                  <span class="secondary-time">{{ c.created_at | date:'HH:mm:ss' }} UTC</span>
                </div>
              </td>
            </ng-container>

            <!-- Dispositivo Column -->
            <ng-container matColumnDef="device">
              <th mat-header-cell *matHeaderCellDef> Dispositivo y SO </th>
              <td mat-cell *matCellDef="let c">
                <div class="device-cell">
                  <span class="device-name">{{ c.device_brand }} {{ c.device_model }}</span>
                  <span class="device-sub">Android {{ c.android_version }} (API {{ c.sdk_int }})</span>
                </div>
              </td>
            </ng-container>

            <!-- Versión App Column -->
            <ng-container matColumnDef="app_version">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Versión App </th>
              <td mat-cell *matCellDef="let c">
                <span class="version-tag">v{{ c.app_version }} ({{ c.build_number }})</span>
              </td>
            </ng-container>

            <!-- Tipo de Error Column -->
            <ng-container matColumnDef="error">
              <th mat-header-cell *matHeaderCellDef> Error y Mensaje </th>
              <td mat-cell *matCellDef="let c" class="error-cell">
                <span class="error-type-tag">{{ c.error_type }}</span>
                <span class="error-desc" [title]="c.error_message || ''">
                  {{ c.error_message || 'Sin mensaje de detalle' }}
                </span>
              </td>
            </ng-container>

            <!-- Usuario Column -->
            <ng-container matColumnDef="user">
              <th mat-header-cell *matHeaderCellDef> Usuario / Rol </th>
              <td mat-cell *matCellDef="let c">
                <div class="user-cell">
                  <span class="user-text">{{ c.user_email || 'Anónimo' }}</span>
                  <span class="user-role-badge" [class.driver]="c.role === 'driver'" [class.passenger]="c.role === 'passenger'">
                    {{ c.role || 'no logueado' }}
                  </span>
                </div>
              </td>
            </ng-container>

            <!-- Acciones Column -->
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef> Acciones </th>
              <td mat-cell *matCellDef="let c" class="actions-cell">
                <button
                  mat-icon-button
                  class="action-btn view-btn"
                  matTooltip="Ver Stack Trace y detalles del dispositivo"
                  (click)="openDetail(c)"
                >
                  <mat-icon>code</mat-icon>
                </button>

                <button
                  mat-icon-button
                  class="action-btn"
                  [class.resolve-btn]="!c.resolved"
                  [class.reopen-btn]="c.resolved"
                  [matTooltip]="c.resolved ? 'Reabrir como pendiente' : 'Marcar como resuelto'"
                  (click)="toggleResolved(c)"
                >
                  <mat-icon>{{ c.resolved ? 'replay' : 'check' }}</mat-icon>
                </button>

                <button
                  mat-icon-button
                  class="action-btn delete-btn"
                  matTooltip="Eliminar reporte de error"
                  (click)="deleteCrash(c)"
                >
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>

            <!-- No data row -->
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty-cell" colspan="7">
                <div class="empty-state">
                  <mat-icon>verified</mat-icon>
                  <p>No hay reportes de error que coincidan con la búsqueda o filtro.</p>
                </div>
              </td>
            </tr>
          </table>
        </div>

        <mat-paginator
          [pageSizeOptions]="[10, 25, 50]"
          pageSize="10"
          showFirstLastButtons
          class="dark-paginator"
        ></mat-paginator>
      </mat-card>
    </div>
  `,
  styles: [`
    .crashes-container {
      padding: 24px;
      max-width: 1400px;
      margin: 0 auto;
    }
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      flex-wrap: wrap;
      gap: 16px;
    }
    .title-group {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .icon-wrap {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background: rgba(239, 68, 68, 0.15);
      display: flex;
      align-items: center;
      justify-content: center;
      mat-icon {
        color: #ef4444;
        font-size: 28px;
        width: 28px;
        height: 28px;
      }
    }
    h2 {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 700;
      color: #f1f5f9;
    }
    .subtitle {
      margin: 4px 0 0 0;
      color: #94a3b8;
      font-size: 0.88rem;
    }
    .refresh-btn {
      background: #0284c7 !important;
      color: #ffffff !important;
      border-radius: 8px;
    }
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }
    .kpi-card {
      background: #1e293b;
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 12px;
      padding: 16px 20px;
      display: flex;
      align-items: center;
      gap: 16px;
      &.pending {
        border-left: 4px solid #ef4444;
        .kpi-icon mat-icon { color: #ef4444; }
      }
      &.resolved {
        border-left: 4px solid #10b981;
        .kpi-icon mat-icon { color: #10b981; }
      }
      &.total {
        border-left: 4px solid #38bdf8;
        .kpi-icon mat-icon { color: #38bdf8; }
      }
    }
    .kpi-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.05);
      display: flex;
      align-items: center;
      justify-content: center;
      mat-icon { font-size: 24px; width: 24px; height: 24px; }
    }
    .kpi-info {
      display: flex;
      flex-direction: column;
    }
    .kpi-label {
      font-size: 0.78rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #94a3b8;
      font-weight: 600;
    }
    .kpi-value {
      font-size: 1.5rem;
      font-weight: 800;
      color: #f8fafc;
    }
    .filter-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;
      margin-bottom: 16px;
    }
    .search-field {
      flex: 1;
      min-width: 280px;
      max-width: 500px;
    }
    .status-filters {
      display: flex;
      gap: 8px;
    }
    .filter-chip {
      border-radius: 20px !important;
      color: #94a3b8 !important;
      border-color: rgba(255, 255, 255, 0.1) !important;
      &.active {
        background: #334155 !important;
        color: #f8fafc !important;
      }
      &.pending-chip.active {
        background: rgba(239, 68, 68, 0.2) !important;
        color: #f87171 !important;
        border-color: rgba(239, 68, 68, 0.4) !important;
      }
      &.resolved-chip.active {
        background: rgba(16, 185, 129, 0.2) !important;
        color: #34d399 !important;
        border-color: rgba(16, 185, 129, 0.4) !important;
      }
    }
    .table-card {
      background: #1e293b;
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 12px;
      overflow: hidden;
      position: relative;
    }
    .table-container {
      overflow-x: auto;
    }
    .dark-table {
      width: 100%;
      background: transparent;
      color: #e2e8f0;
      th {
        background: #0f172a;
        color: #94a3b8;
        font-size: 0.78rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        border-bottom: 1px solid rgba(255, 255, 255, 0.08);
      }
      td {
        border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      }
    }
    .status-pill {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 600;
      .pill-icon {
        font-size: 14px;
        width: 14px;
        height: 14px;
      }
      &.pending {
        background: rgba(239, 68, 68, 0.15);
        color: #fca5a5;
        border: 1px solid rgba(239, 68, 68, 0.3);
      }
      &.resolved {
        background: rgba(16, 185, 129, 0.15);
        color: #6ee7b7;
        border: 1px solid rgba(16, 185, 129, 0.3);
      }
    }
    .date-cell {
      display: flex;
      flex-direction: column;
      .primary-date { font-weight: 600; color: #f1f5f9; font-size: 0.85rem; }
      .secondary-time { font-size: 0.72rem; color: #64748b; }
    }
    .device-cell {
      display: flex;
      flex-direction: column;
      .device-name { font-weight: 600; color: #38bdf8; font-size: 0.85rem; }
      .device-sub { font-size: 0.72rem; color: #94a3b8; }
    }
    .version-tag {
      background: #334155;
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 0.75rem;
      font-family: monospace;
      color: #e2e8f0;
    }
    .error-cell {
      max-width: 280px;
      .error-type-tag {
        display: block;
        font-family: monospace;
        font-size: 0.8rem;
        font-weight: 700;
        color: #f87171;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .error-desc {
        display: block;
        font-size: 0.75rem;
        color: #94a3b8;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
    .user-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
      .user-text { font-size: 0.82rem; color: #cbd5e1; }
      .user-role-badge {
        font-size: 0.68rem;
        text-transform: uppercase;
        color: #64748b;
        font-weight: 600;
        &.driver { color: #f59e0b; }
        &.passenger { color: #38bdf8; }
      }
    }
    .actions-cell {
      white-space: nowrap;
    }
    .action-btn {
      color: #94a3b8;
      &.view-btn { color: #38bdf8; }
      &.resolve-btn { color: #10b981; }
      &.reopen-btn { color: #f59e0b; }
      &.delete-btn { color: #ef4444; }
    }
    .empty-cell {
      padding: 40px !important;
    }
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      color: #64748b;
      mat-icon { font-size: 36px; width: 36px; height: 36px; color: #10b981; }
      p { margin: 0; font-size: 0.9rem; }
    }
    .loading-shade {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(15, 23, 42, 0.75);
      z-index: 10;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      color: #f8fafc;
    }
    .dark-paginator {
      background: #0f172a;
      color: #94a3b8;
      border-top: 1px solid rgba(255, 255, 255, 0.05);
    }
  `]
})
export class CrashLogsComponent implements OnInit {
  private readonly supabaseService = inject(SupabaseService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly displayedColumns: string[] = [
    'status',
    'created_at',
    'device',
    'app_version',
    'error',
    'user',
    'actions',
  ];

  readonly dataSource = new MatTableDataSource<CrashLog>([]);
  readonly isLoading = signal<boolean>(false);
  readonly currentFilter = signal<'all' | 'pending' | 'resolved'>('all');

  private allCrashes: CrashLog[] = [];

  readonly totalCount = signal<number>(0);
  readonly pendingCount = signal<number>(0);
  readonly resolvedCount = signal<number>(0);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  ngOnInit(): void {
    this.loadCrashLogs();
  }

  async loadCrashLogs(): Promise<void> {
    this.isLoading.set(true);
    try {
      this.allCrashes = await this.supabaseService.getCrashLogs();
      this.updateCounts();
      this.applyCurrentFilter();
    } catch {
      this.snackBar.open('Error al cargar la lista de fallos', 'Cerrar', { duration: 3500 });
    } finally {
      this.isLoading.set(false);
    }
  }

  private updateCounts(): void {
    this.totalCount.set(this.allCrashes.length);
    this.pendingCount.set(this.allCrashes.filter(c => !c.resolved).length);
    this.resolvedCount.set(this.allCrashes.filter(c => c.resolved).length);
  }

  setFilter(filter: 'all' | 'pending' | 'resolved'): void {
    this.currentFilter.set(filter);
    this.applyCurrentFilter();
  }

  private applyCurrentFilter(): void {
    let filtered = this.allCrashes;
    const filter = this.currentFilter();

    if (filter === 'pending') {
      filtered = this.allCrashes.filter(c => !c.resolved);
    } else if (filter === 'resolved') {
      filtered = this.allCrashes.filter(c => c.resolved);
    }

    this.dataSource.data = filtered;
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  applyFilter(event: Event): void {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
  }

  openDetail(crash: CrashLog): void {
    const dialogRef = this.dialog.open(CrashDetailDialogComponent, {
      width: '800px',
      maxWidth: '95vw',
      data: { crash: { ...crash } },
      panelClass: 'dark-dialog'
    });

    dialogRef.afterClosed().subscribe(res => {
      if (res?.updated) {
        this.loadCrashLogs();
      }
    });
  }

  async toggleResolved(crash: CrashLog): Promise<void> {
    try {
      const newStatus = !crash.resolved;
      await this.supabaseService.setCrashLogResolved(crash.id, newStatus);
      crash.resolved = newStatus;
      this.updateCounts();
      this.applyCurrentFilter();
      this.snackBar.open(
        newStatus ? 'Incidencia marcada como resuelta' : 'Incidencia reabierta como pendiente',
        'Cerrar',
        { duration: 3000 }
      );
    } catch {
      this.snackBar.open('Error al actualizar estado', 'Cerrar', { duration: 3000 });
    }
  }

  async deleteCrash(crash: CrashLog): Promise<void> {
    const confirmed = window.confirm(`¿Deseas eliminar permanentemente este reporte de error (${crash.error_type})?`);
    if (!confirmed) return;

    try {
      await this.supabaseService.deleteCrashLog(crash.id);
      this.allCrashes = this.allCrashes.filter(c => c.id !== crash.id);
      this.updateCounts();
      this.applyCurrentFilter();
      this.snackBar.open('Reporte eliminado con éxito', 'Cerrar', { duration: 3000 });
    } catch {
      this.snackBar.open('Error al eliminar reporte', 'Cerrar', { duration: 3000 });
    }
  }
}

