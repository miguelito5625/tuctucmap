import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
  ElementRef,
  ViewChild,
  AfterViewInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { SupabaseService } from '../../services/supabase.service';
import { LiveDriverLocation } from '../../core/models/driver.model';
import * as L from 'leaflet';

@Component({
  selector: 'app-fleet-map',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatChipsModule,
  ],
  template: `
    <div class="fleet-page">
      <header class="page-header">
        <div class="page-title-group">
          <div class="page-title-icon">
            <mat-icon>map</mat-icon>
          </div>
          <div>
            <h1 class="page-title">Mapa en Vivo de la Flota</h1>
            <p class="page-subtitle">Monitoreo geográfico de toritos conectados en tiempo real vía Firebase</p>
          </div>
        </div>
        <div class="fleet-actions">
          <button mat-stroked-button class="fit-btn" (click)="fitFleetBounds()" [disabled]="activeDrivers().length === 0">
            <mat-icon>crop_free</mat-icon>
            Encuadrar Flota
          </button>
          <button mat-flat-button class="refresh-btn" (click)="loadFleet()">
            <mat-icon>refresh</mat-icon>
            Actualizar Flota
          </button>
        </div>
      </header>

      <div class="fleet-content">
        <!-- Mapa Principal -->
        <mat-card class="map-card">
          <div #mapContainer class="map-viewport"></div>
        </mat-card>

        <!-- Panel Lateral de Toritos Activos -->
        <div class="fleet-sidebar">
          <div class="sidebar-header">
            <h3>Toritos Circulando</h3>
            <span class="active-badge">{{ activeDrivers().length }} en línea</span>
          </div>

          <div class="driver-list">
            @if (activeDrivers().length === 0) {
              <div class="empty-state">
                <mat-icon>directions_car</mat-icon>
                <p>No hay conductores conectados en este momento.</p>
              </div>
            } @else {
              @for (driver of activeDrivers(); track driver.driver_id) {
                <div class="driver-card" (click)="focusDriver(driver)">
                  <div class="driver-card-avatar">🛺</div>
                  <div class="driver-card-info">
                    <span class="driver-name">{{ driver.nombre || 'Conductor' }}</span>
                    <span class="driver-plate">Placas: {{ driver.placas }}</span>
                    <span class="driver-status" [class]="'status--' + (driver.status || 'disponible')">
                      ● {{ (driver.status || 'disponible') | uppercase }}
                    </span>
                  </div>
                  <button
                    mat-icon-button
                    class="focus-btn"
                    matTooltip="Centrar en el mapa"
                    (click)="focusDriver(driver); $event.stopPropagation()"
                  >
                    <mat-icon>my_location</mat-icon>
                  </button>
                </div>
              }
            }
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .fleet-page {
      padding: 24px 32px;
      display: flex;
      flex-direction: column;
      gap: 20px;
      height: calc(100vh - 80px);
      box-sizing: border-box;
    }

    .page-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .page-title-group {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .page-title-icon {
      width: 48px;
      height: 48px;
      border-radius: 14px;
      background: linear-gradient(135deg, rgba(34, 197, 94, 0.2), rgba(16, 185, 129, 0.2));
      border: 1px solid rgba(34, 197, 94, 0.3);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #4ade80;

      mat-icon { font-size: 26px; width: 26px; height: 26px; }
    }

    .page-title {
      margin: 0;
      font-size: 1.45rem;
      font-weight: 800;
      color: #f1f5f9;
    }

    .page-subtitle {
      margin: 2px 0 0;
      font-size: 0.85rem;
      color: #94a3b8;
    }

    .fleet-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .fit-btn {
      color: #94a3b8 !important;
      border-color: rgba(255, 255, 255, 0.2) !important;
      border-radius: 10px !important;
      &:hover:not([disabled]) {
        color: #f8fafc !important;
        border-color: rgba(255, 255, 255, 0.4) !important;
      }
    }

    .refresh-btn {
      background: rgba(59, 130, 246, 0.2) !important;
      color: #60a5fa !important;
      border: 1px solid rgba(59, 130, 246, 0.3) !important;
      border-radius: 10px !important;
    }

    .fleet-content {
      display: flex;
      gap: 20px;
      flex: 1;
      min-height: 0;
    }

    .map-card {
      flex: 1;
      height: 100%;
      border-radius: 16px;
      overflow: hidden;
      border: 1px solid rgba(255, 255, 255, 0.08);
      background: #0f172a;
    }

    .map-viewport {
      width: 100%;
      height: 100%;
      min-height: 350px;
    }

    .fleet-sidebar {
      width: 340px;
      background: rgba(30, 41, 59, 0.7);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 16px;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .sidebar-header {
      padding: 18px 20px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid rgba(255, 255, 255, 0.07);

      h3 {
        margin: 0;
        font-size: 1.05rem;
        font-weight: 700;
        color: #f8fafc;
      }
    }

    .active-badge {
      background: rgba(34, 197, 94, 0.15);
      color: #4ade80;
      border: 1px solid rgba(34, 197, 94, 0.3);
      padding: 2px 10px;
      border-radius: 20px;
      font-size: 0.75rem;
      font-weight: 700;
    }

    .driver-list {
      flex: 1;
      overflow-y: auto;
      padding: 12px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .driver-card {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 12px;
      cursor: pointer;
      transition: all 0.2s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.06);
        border-color: rgba(59, 130, 246, 0.4);
      }
    }

    .driver-card-avatar {
      font-size: 24px;
      width: 40px;
      height: 40px;
      background: rgba(249, 212, 35, 0.15);
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .driver-card-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .driver-name {
      font-size: 0.9rem;
      font-weight: 700;
      color: #f1f5f9;
    }

    .driver-plate {
      font-size: 0.78rem;
      color: #f9d423;
      font-family: monospace;
    }

    .driver-status {
      font-size: 0.72rem;
      font-weight: 600;

      &.status--disponible { color: #4ade80; }
      &.status--ocupado { color: #fbbf24; }
      &.status--en_pausa { color: #94a3b8; }
    }

    .focus-btn {
      color: #94a3b8;
      &:hover { color: #60a5fa; }
    }

    .empty-state {
      padding: 40px 20px;
      text-align: center;
      color: #64748b;

      mat-icon { font-size: 44px; width: 44px; height: 44px; opacity: 0.4; margin-bottom: 8px; }
      p { margin: 0; font-size: 0.85rem; }
    }

    /* ── Responsive Media Queries ───────────────────────────── */
    @media (max-width: 991.98px) {
      .fleet-page {
        padding: 16px;
        height: auto;
        min-height: calc(100vh - 64px);
      }

      .fleet-content {
        flex-direction: column;
        height: auto;
      }

      .map-card {
        height: 420px;
        min-height: 350px;
        flex: none;
      }

      .fleet-sidebar {
        width: 100%;
        max-height: 380px;
      }

      .driver-list {
        max-height: 300px;
      }
    }

    @media (max-width: 640px) {
      .fleet-page {
        padding: 12px;
        gap: 14px;
      }

      .page-header {
        flex-direction: column;
        align-items: stretch;
        gap: 14px;
      }

      .page-title-group {
        gap: 12px;
      }

      .page-title {
        font-size: 1.2rem;
      }

      .fleet-actions {
        width: 100%;
        gap: 8px;

        button {
          flex: 1;
          padding: 0 8px;
          font-size: 0.8rem;
        }
      }

      .map-card {
        height: 340px;
        min-height: 280px;
      }
    }
  `],
})
export class FleetMapComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('mapContainer', { static: false }) mapContainer!: ElementRef<HTMLDivElement>;

  private readonly supabaseService = inject(SupabaseService);
  private map: L.Map | null = null;
  private markers = new Map<string, L.Marker>();
  private pollInterval: any = null;

  readonly activeDrivers = signal<LiveDriverLocation[]>([]);

  ngOnInit(): void {
    this.loadFleet();
    // Sondeo de telemetría cada 8 segundos
    this.pollInterval = setInterval(() => this.loadFleet(), 8000);
  }

  ngAfterViewInit(): void {
    this.initMap();
  }

  ngOnDestroy(): void {
    if (this.pollInterval) clearInterval(this.pollInterval);
    if (this.map) this.map.remove();
  }

  private initMap(): void {
    if (!this.mapContainer) return;

    // Coordenadas iniciales (Centro de Guatemala)
    this.map = L.map(this.mapContainer.nativeElement).setView([14.6349, -90.5069], 14);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(this.map);

    this.renderMarkers();
  }

  private hasInitiallyCentered = false;

  async loadFleet(): Promise<void> {
    const drivers = await this.supabaseService.getLiveDrivers();
    this.activeDrivers.set(drivers);
    this.renderMarkers();
    if (!this.hasInitiallyCentered && drivers.length > 0) {
      this.fitFleetBounds();
      this.hasInitiallyCentered = true;
    }
  }

  fitFleetBounds(): void {
    const mapInstance = this.map;
    const drivers = this.activeDrivers();
    if (!mapInstance || drivers.length === 0) return;

    const validPoints = drivers
      .filter((d) => d.lat != null && d.lon != null)
      .map((d) => L.latLng(d.lat, d.lon));

    if (validPoints.length > 0) {
      const bounds = L.latLngBounds(validPoints);
      mapInstance.fitBounds(bounds, { padding: [50, 50], maxZoom: 16 });
    }
  }

  private renderMarkers(): void {
    const mapInstance = this.map;
    if (!mapInstance) return;

    const currentUids = new Set(this.activeDrivers().map((d) => d.driver_id));

    // Remover marcadores de conductores desconectados
    for (const [uid, marker] of this.markers.entries()) {
      if (!currentUids.has(uid)) {
        marker.remove();
        this.markers.delete(uid);
      }
    }

    // Agregar o actualizar marcadores
    this.activeDrivers().forEach((driver) => {
      const lat = driver.lat;
      const lon = driver.lon;
      if (!lat || !lon) return;

      const isBusy = driver.status === 'ocupado';
      const iconHtml = `
        <div style="
          background: ${isBusy ? '#ea580c' : '#16a34a'};
          color: white;
          border-radius: 50%;
          width: 36px;
          height: 36px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 18px;
          border: 2px solid white;
          box-shadow: 0 4px 10px rgba(0,0,0,0.5);
        ">
          🛺
        </div>
      `;

      const customIcon = L.divIcon({
        html: iconHtml,
        className: 'custom-tuctuc-marker',
        iconSize: [36, 36],
        iconAnchor: [18, 18],
      });

      const safeName = this.escapeHtml(driver.nombre || 'Conductor');
      const safePlate = this.escapeHtml(driver.placas || '');
      const safePhone = this.escapeHtml(driver.telefono || '');
      const safeStatus = this.escapeHtml(driver.status || 'Disponible');

      const popupContent = `
        <div style="font-family: sans-serif; color: #1e293b; padding: 4px;">
          <h4 style="margin: 0 0 4px; font-size: 14px; font-weight: bold;">${safeName}</h4>
          <p style="margin: 0 0 2px; font-size: 12px; color: #475569;"><b>Placas:</b> ${safePlate}</p>
          <p style="margin: 0 0 2px; font-size: 12px; color: #475569;"><b>Teléfono:</b> ${safePhone}</p>
          <p style="margin: 0; font-size: 12px; color: ${isBusy ? '#ea580c' : '#16a34a'}; font-weight: bold;">
            Estado: ${safeStatus}
          </p>
        </div>
      `;

      if (this.markers.has(driver.driver_id)) {
        const marker = this.markers.get(driver.driver_id)!;
        marker.setLatLng([lat, lon]);
        marker.setIcon(customIcon);
        marker.setPopupContent(popupContent);
      } else {
        const marker = L.marker([lat, lon], { icon: customIcon })
          .addTo(mapInstance)
          .bindPopup(popupContent);
        this.markers.set(driver.driver_id, marker);
      }
    });
  }

  private escapeHtml(raw: string): string {
    return String(raw)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  focusDriver(driver: LiveDriverLocation): void {
    const mapInstance = this.map;
    if (!mapInstance || !driver.lat || !driver.lon) return;
    mapInstance.setView([driver.lat, driver.lon], 16, { animate: true });
    const marker = this.markers.get(driver.driver_id);
    if (marker) marker.openPopup();
  }
}
